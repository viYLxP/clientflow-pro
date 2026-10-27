package com.clientflow.payment;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.invoice.Invoice;

/**
 * Tamara instalments: the customer splits the amount into 4 payments; we receive it in full.
 * تقسيط تمارا: العميل يقسم المبلغ على 4 دفعات؛ واحنا نستلمه كامل.
 *
 * <p>Flow (Tamara API): create a checkout and send the customer to checkout_url. When they finish, the order is
 * "approved"; we must then authorise it, and from "authorised" the order counts as paid (Tamara captures it
 * automatically after 21 days if we do not).</p>
 * <p>المسار (Tamara API): ننشئ checkout وننقل العميل لـ checkout_url. لما يخلّص يصير الطلب "approved"؛ ولازم
 * بعدها نسوي له authorise، ومن "authorised" ينحسب الطلب مدفوع (تمارا تسوي capture تلقائي بعد 21 يوم لو ما سويناه).</p>
 */
public class TamaraGateway implements PaymentGateway {

    private final RestClient client;
    private final int instalments;

    public TamaraGateway(RestClient.Builder builder, String apiUrl, String apiToken, int instalments) {
        this.client = builder.baseUrl(apiUrl)
                .defaultHeaders(h -> h.setBearerAuth(apiToken))
                .build();
        this.instalments = instalments;
    }

    @Override
    public Payment.Provider provider() {
        return Payment.Provider.TAMARA;
    }

    @Override
    public Checkout start(Payment payment, Urls urls) {
        Invoice inv = payment.getInvoice();
        GatewaySupport.Payer payer = GatewaySupport.payer(inv);
        String currency = payment.getCurrency();
        String[] names = splitName(payer.name());
        Map<String, Object> consumer = new LinkedHashMap<>();
        consumer.put("first_name", names[0]);
        consumer.put("last_name", names[1]);
        if (payer.internationalPhone() != null) {
            consumer.put("phone_number", payer.internationalPhone());
        }
        if (payer.email() != null) {
            consumer.put("email", payer.email());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        // Our payment id is unique per attempt, so a retry never collides with an older Tamara order.
        // رقم دفعتنا فريد لكل محاولة، فإعادة المحاولة ما تتعارض مع طلب تمارا قديم.
        body.put("order_reference_id", payment.getId().toString());
        body.put("order_number", inv.getNumber());
        body.put("total_amount", money(payment.getAmount(), currency));
        body.put("shipping_amount", money(BigDecimal.ZERO, currency));
        body.put("tax_amount", money(inv.getVat(), currency));
        body.put("description", "ClientFlow " + inv.getNumber());
        body.put("country_code", "SA");
        body.put("payment_type", "PAY_BY_INSTALMENTS");
        body.put("instalments", instalments);
        body.put("locale", "ar_SA");
        body.put("items", inv.getLines().stream().map(l -> Map.<String, Object>of("name", l.getDescription(),
                "type", "Digital", "reference_id", inv.getNumber() + "-" + (l.getPosition() + 1),
                "sku", inv.getNumber() + "-" + (l.getPosition() + 1), "quantity", l.getQuantity(),
                "unit_price", money(l.getUnitPrice(), currency),
                "total_amount", money(l.getLineTotal(), currency))).toList());
        body.put("consumer", consumer);
        // Services have no delivery; Tamara still asks for an address, so the company's city is sent.
        // الخدمات ما لها توصيل؛ بس تمارا تطلب عنوان، فنرسل مدينة الشركة.
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("first_name", names[0]);
        address.put("last_name", names[1]);
        address.put("line1", payer.city());
        address.put("city", payer.city());
        address.put("country_code", "SA");
        if (payer.internationalPhone() != null) {
            address.put("phone_number", payer.internationalPhone());
        }
        body.put("shipping_address", address);
        body.put("merchant_url", Map.of("success", urls.successUrl(), "failure", urls.failureUrl(),
                "cancel", urls.backUrl(), "notification", urls.successUrl()));
        body.put("risk_assessment", Map.of());
        Order order = GatewaySupport.call("Tamara", () -> client.post().uri("/checkout")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Order.class));
        if (order == null || order.order_id() == null || order.checkout_url() == null) {
            throw new ApiException(ErrorCode.INSTALMENTS_NOT_AVAILABLE);
        }
        return new Checkout(order.order_id(), order.checkout_url());
    }

    @Override
    public Optional<Result> fetch(Payment payment) {
        Order order = GatewaySupport.call("Tamara", () -> client.get()
                .uri("/merchants/orders/{id}", payment.getProviderRef()).retrieve().body(Order.class));
        if (order == null) {
            return Optional.empty();
        }
        if (order.total_amount() != null && !GatewaySupport.same(order.total_amount().amount(), payment.getAmount())) {
            return Optional.of(new Result(Payment.Status.FAILED, "tamara", null, "AMOUNT_MISMATCH"));
        }
        return switch (order.status() == null ? "" : order.status()) {
            case "approved" -> {
                Order authorised = GatewaySupport.call("Tamara", () -> client.post()
                        .uri("/orders/{id}/authorise", payment.getProviderRef()).retrieve().body(Order.class));
                yield authorised != null && "authorised".equals(authorised.status())
                        ? Optional.of(new Result(Payment.Status.PAID, "tamara", null, null))
                        : Optional.empty();
            }
            case "authorised", "partially_captured", "fully_captured" ->
                    Optional.of(new Result(Payment.Status.PAID, "tamara", null, null));
            case "declined", "expired", "canceled" ->
                    Optional.of(new Result(Payment.Status.FAILED, "tamara", null, order.status().toUpperCase()));
            default -> Optional.empty();
        };
    }

    @Override
    public void refund(Payment payment) {
        GatewaySupport.call("Tamara", () -> client.post()
                .uri("/payments/simplified-refund/{id}", payment.getProviderRef())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("total_amount", money(payment.getAmount(), payment.getCurrency()),
                        "comment", "Invoice " + payment.getInvoice().getNumber() + " voided"))
                .retrieve().toBodilessEntity());
    }

    private static Map<String, Object> money(BigDecimal amount, String currency) {
        return Map.of("amount", amount, "currency", currency);
    }

    // Tamara wants first and last name apart; a one-word name repeats it. / تمارا تبي الاسم الأول والأخير منفصلين.
    static String[] splitName(String fullName) {
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? new String[]{trimmed, trimmed}
                : new String[]{trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Order(String order_id, String checkout_url, String status, Amount total_amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Amount(BigDecimal amount, String currency) {
    }
}
