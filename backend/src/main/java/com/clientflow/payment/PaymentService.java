package com.clientflow.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.common.ApiException;
import com.clientflow.common.CurrentUser;
import com.clientflow.common.ErrorCode;
import com.clientflow.config.AppProperties;
import com.clientflow.invoice.Invoice;
import com.clientflow.invoice.InvoiceLock;
import com.clientflow.invoice.InvoiceStatus;
import com.clientflow.payment.PaymentDtos.CheckoutPage;
import com.clientflow.payment.PaymentDtos.CheckoutResponse;
import com.clientflow.payment.PaymentDtos.Instalment;
import com.clientflow.payment.PaymentDtos.PaymentState;
import com.clientflow.payment.PaymentDtos.PaymentSummary;
import com.clientflow.payment.PaymentDtos.PaymentView;
import com.clientflow.payment.PaymentDtos.SandboxRequest;

/**
 * Payment rules for invoices: when an invoice can be paid, online checkout (card, Apple Pay, Tabby, Tamara),
 * bank transfers recorded by staff, and refunds when a paid invoice is voided.
 * قواعد الدفع للفواتير: متى تنْدفع الفاتورة، والدفع أونلاين (بطاقة، Apple Pay، تابي، تمارا)، والتحويل البنكي اللي
 * يسجّله الموظف، والاسترجاع لما تنلغي فاتورة مدفوعة.
 *
 * <p>The amount is always the invoice total read from the database, never a number sent by the browser.</p>
 * <p>المبلغ دايم هو إجمالي الفاتورة من قاعدة البيانات، أبدًا رقم جاي من المتصفح.</p>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // Gateways refuse tiny charges (Moyasar's minimum is 1 SAR). / البوابات ترفض المبالغ الصغيرة جدًا (أقل شي عند Moyasar ريال).
    private static final BigDecimal MIN_ONLINE = BigDecimal.ONE;

    private final PaymentRepository payments;
    private final PaymentGateways gateways;
    private final InvoiceLock locks;
    private final ZoneId zone;
    private final String publicUrl;
    private final AppProperties.Instalments instalments;

    public PaymentService(PaymentRepository payments, PaymentGateways gateways, InvoiceLock locks,
                          AppProperties properties) {
        this.payments = payments;
        this.gateways = gateways;
        this.locks = locks;
        this.zone = properties.timeZone();
        this.instalments = properties.payments().instalments();
        // Without the trailing slash, so links never get "//". / بدون الشرطة الأخيرة، حتى الروابط ما يصير فيها "//".
        this.publicUrl = properties.payments().publicUrl().replaceAll("/+$", "");
    }

    /**
     * Opens a checkout with the chosen method. Reuses a still-open attempt of the same method, so pressing "Pay"
     * twice does not create two checkouts the client could both pay. The caller locks the invoice first.
     * يفتح الدفع بالطريقة المختارة. يعيد استخدام المحاولة المفتوحة لنفس الطريقة، حتى الضغط على "ادفع" مرتين ما
     * يسوي دفعتين ممكن العميل يدفعهم الثنتين. واللي يناديها يقفل الفاتورة أول.
     */
    @Transactional
    public CheckoutResponse startOnline(Invoice invoice, Payment.Method method) {
        requireOnlinePayable(invoice);
        if (!PaymentGateways.ONLINE.contains(method)) {
            throw fieldError("method", "Online");
        }
        BigDecimal amount = invoice.getTotal();
        if (method.isInstalments() && !instalmentsAllowed(amount)) {
            throw new ApiException(ErrorCode.INSTALMENTS_NOT_AVAILABLE,
                    Map.of("min", instalments.minAmount(), "max", instalments.maxAmount()));
        }
        PaymentGateway gateway = gateways.forMethod(method);
        for (Payment open : payments.findByInvoiceAndStatus(invoice, Payment.Status.PENDING)) {
            refresh(open);
            if (invoice.getStatus() != InvoiceStatus.ISSUED) {
                // It turned out the client had already paid with this attempt. / طلع إن العميل دفع بهالمحاولة.
                throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
            }
            if (open.getStatus() != Payment.Status.PENDING || open.getMethod() != method) {
                continue;
            }
            if (open.getProvider() == gateway.provider() && open.getAmount().compareTo(amount) == 0) {
                return new CheckoutResponse(open.getId(), open.getCheckoutUrl());
            }
            // The gateway changed since: close the old attempt. / البوابة تغيّرت: نقفل المحاولة القديمة.
            open.markFailed("SUPERSEDED", null, null);
        }
        Payment payment = new Payment(invoice, method, gateway.provider(), amount, CurrentUser.username(),
                Instant.now());
        PaymentGateway.Checkout checkout = gateway.start(payment, urls(payment));
        payment.setProviderRef(checkout.providerRef());
        payment.setCheckoutUrl(checkout.url());
        payments.save(payment);
        return new CheckoutResponse(payment.getId(), checkout.url());
    }

    /**
     * The payment page. For a real gateway it first asks the gateway for the latest status, so a client coming
     * back from Moyasar sees "paid" at once.
     * صفحة الدفع. للبوابة الحقيقية تسأل البوابة أول عن آخر حالة، حتى العميل الراجع من Moyasar يشوف "مدفوع" على طول.
     */
    @Transactional
    public CheckoutPage page(UUID id) {
        Payment payment = find(id);
        if (payment.getStatus() == Payment.Status.PENDING) {
            locks.lock(payment.getInvoice());
        }
        refresh(payment);
        return page(payment);
    }

    /**
     * The client's answer on the sandbox page: a test card, or approving / cancelling Apple Pay, Tabby or Tamara.
     * A declined card leaves the attempt open for another card.
     * رد العميل في صفحة الدفع التجريبية: بطاقة تجريبية، أو الموافقة / الإلغاء في Apple Pay أو تابي أو تمارا.
     * البطاقة المرفوضة تخلي المحاولة مفتوحة لبطاقة ثانية.
     */
    @Transactional
    public CheckoutPage payInSandbox(UUID id, SandboxRequest answer) {
        Payment payment = find(id);
        if (payment.getProvider() != Payment.Provider.SANDBOX) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        if (payment.getStatus() == Payment.Status.PAID) {
            return page(payment);
        }
        if (payment.getStatus() == Payment.Status.REFUNDED || "SUPERSEDED".equals(payment.getFailureReason())) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        locks.lock(payment.getInvoice());
        requireOnlinePayable(payment.getInvoice());
        if (payment.getMethod() == Payment.Method.CARD) {
            chargeTestCard(payment, answer);
        } else if (answer.approve() == null) {
            throw fieldError("approve", "NotNull");
        } else if (answer.approve()) {
            settle(payment, payment.getMethod().name().toLowerCase(), null);
        } else {
            payment.markFailed("CANCELLED_BY_CUSTOMER", null, null);
        }
        return page(payment);
    }

    private void chargeTestCard(Payment payment, SandboxRequest card) {
        Map<String, String> missing = new LinkedHashMap<>();
        if (card.cardNumber() == null || card.cardNumber().isBlank()) {
            missing.put("cardNumber", "NotBlank");
        }
        if (card.expMonth() == null) {
            missing.put("expMonth", "NotNull");
        }
        if (card.expYear() == null) {
            missing.put("expYear", "NotNull");
        }
        if (card.cvc() == null || card.cvc().isBlank()) {
            missing.put("cvc", "NotBlank");
        }
        if (card.cardHolder() == null || card.cardHolder().isBlank()) {
            missing.put("cardHolder", "NotBlank");
        }
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", missing));
        }
        String digits = SandboxCards.clean(card.cardNumber());
        if (digits == null) {
            throw fieldError("cardNumber", "CardNumber");
        }
        if (!SandboxCards.expiryValid(card.expMonth(), card.expYear(), YearMonth.now(zone))) {
            throw fieldError("expYear", "CardExpired");
        }
        SandboxCards.Outcome outcome = SandboxCards.charge(digits);
        if (outcome.approved()) {
            settle(payment, outcome.brand(), outcome.last4());
        } else {
            payment.markFailed(outcome.failureReason(), outcome.brand(), outcome.last4());
        }
    }

    /**
     * Records a bank transfer the accountant saw on the statement. The caller locks the invoice first.
     * يسجّل تحويل بنكي شافه المحاسب في الكشف. واللي يناديها يقفل الفاتورة أول.
     */
    @Transactional
    public void recordBankTransfer(Invoice invoice, String reference) {
        // A payment made online at the last second wins; then there is nothing left to record.
        // لو العميل دفع أونلاين بآخر ثانية فهو اللي ينحسب؛ وما يبقى شي نسجّله.
        refreshPending(invoice);
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        Instant now = Instant.now();
        Payment payment = new Payment(invoice, Payment.Method.BANK_TRANSFER, Payment.Provider.MANUAL,
                invoice.getTotal(), CurrentUser.username(), now);
        payment.setProviderRef(reference);
        payment.markPaid(now, null, null);
        invoice.markPaid(now);
        // An open online attempt can no longer be used. / محاولة الدفع المفتوحة ما عاد تنفع.
        payments.findByInvoiceAndStatus(invoice, Payment.Status.PENDING)
                .forEach(p -> p.markFailed("SUPERSEDED", null, null));
        payments.save(payment);
    }

    /**
     * Asks the gateways about the invoice's open attempts, so a payment made at the last second is not missed.
     * The caller locks the invoice first.
     * يسأل البوابات عن المحاولات المفتوحة للفاتورة، حتى ما يفوتنا دفع صار بآخر ثانية. واللي يناديها يقفل الفاتورة أول.
     */
    @Transactional
    public void refreshPending(Invoice invoice) {
        payments.findByInvoiceAndStatus(invoice, Payment.Status.PENDING).forEach(this::refresh);
    }

    /**
     * Called when an invoice is voided: returns the money of a paid invoice and closes open attempts.
     * A bank transfer is marked refunded; the transfer back is done by the accountant outside the system.
     * ينادى لما تنلغي فاتورة: يرجّع فلوس الفاتورة المدفوعة ويقفل المحاولات المفتوحة.
     * التحويل البنكي ينعلّم كمسترجع؛ والتحويل الراجع يسويه المحاسب برا النظام.
     */
    @Transactional
    public void refundIfPaid(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.PAID) {
            payments.findByInvoiceAndStatus(invoice, Payment.Status.PENDING)
                    .forEach(p -> p.markFailed("SUPERSEDED", null, null));
            return;
        }
        Payment paid = payments.findFirstByInvoiceAndStatus(invoice, Payment.Status.PAID)
                .orElseThrow(() -> new IllegalStateException("PAID invoice without a paid payment"));
        if (paid.getProvider() != Payment.Provider.MANUAL) {
            // The gateway is called first: if it fails, voiding is rolled back with it.
            // نتصل بالبوابة أول: لو فشلت، الإلغاء يتراجع معها.
            gateways.of(paid.getProvider()).refund(paid);
        }
        paid.markRefunded(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PaymentView> history(Invoice invoice) {
        return payments.findByInvoiceOrderByCreatedAtAsc(invoice).stream().map(PaymentView::of).toList();
    }

    @Transactional(readOnly = true)
    public PaymentSummary summary(Invoice invoice) {
        Payment settled = payments.findByInvoiceOrderByCreatedAtAsc(invoice).stream()
                .filter(p -> p.getStatus() == Payment.Status.PAID || p.getStatus() == Payment.Status.REFUNDED)
                .reduce((a, b) -> b).orElse(null);
        PaymentState state = settled == null ? PaymentState.UNPAID
                : settled.getStatus() == Payment.Status.PAID ? PaymentState.PAID : PaymentState.REFUNDED;
        BigDecimal due = invoice.getTotal();
        boolean payable = isOnlinePayable(invoice);
        List<Payment.Method> methods = !payable ? List.of() : PaymentGateways.ONLINE.stream()
                .filter(m -> !m.isInstalments() || instalmentsAllowed(due)).toList();
        return new PaymentSummary(state, payable, due, methods,
                payable && instalmentsAllowed(due) ? plan(due) : List.of(),
                settled == null ? null : settled.getMethod(),
                settled == null ? null : settled.getCardBrand(),
                settled == null ? null : settled.getCardLast4(),
                settled == null ? null : settled.getPaidAt());
    }

    // Issued and not paid yet, and not too small for a gateway. / صادرة وما اندفعت، ومو أصغر من الحد اللي تقبله البوابات.
    public boolean isOnlinePayable(Invoice invoice) {
        return invoice.getStatus() == InvoiceStatus.ISSUED && invoice.getTotal().compareTo(MIN_ONLINE) >= 0;
    }

    private boolean instalmentsAllowed(BigDecimal amount) {
        return amount != null && amount.compareTo(instalments.minAmount()) >= 0
                && amount.compareTo(instalments.maxAmount()) <= 0;
    }

    /**
     * Equal monthly instalments, the first one today. Any halala left over from the division goes on the first,
     * so the instalments always add up to the exact total.
     * دفعات شهرية متساوية، أولها اليوم. الهللة الزايدة من القسمة تنضاف على الأولى، فمجموع الدفعات يساوي الإجمالي بالضبط.
     */
    List<Instalment> plan(BigDecimal total) {
        int count = instalments.count();
        BigDecimal each = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal first = total.subtract(each.multiply(BigDecimal.valueOf(count - 1)));
        LocalDate today = LocalDate.now(zone);
        List<Instalment> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new Instalment(i + 1, i == 0 ? first : each, today.plusMonths(i)));
        }
        return list;
    }

    private void requireOnlinePayable(Invoice invoice) {
        if (!isOnlinePayable(invoice)) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
    }

    // Asks a real gateway about an open attempt and applies the answer. / يسأل البوابة الحقيقية عن محاولة مفتوحة ويطبّق الرد.
    private void refresh(Payment payment) {
        if (payment.getStatus() != Payment.Status.PENDING || payment.getProvider() == Payment.Provider.SANDBOX) {
            return;
        }
        gateways.of(payment.getProvider()).fetch(payment).ifPresent(result -> {
            if (result.status() == Payment.Status.PAID) {
                settle(payment, result.brand(), result.last4());
            } else if (result.status() == Payment.Status.FAILED) {
                payment.markFailed(result.failureReason(), result.brand(), result.last4());
            }
        });
    }

    /**
     * Marks the payment and the invoice as paid. If the invoice was already paid another way (a bank transfer, or a
     * second checkout) or was voided, this money is returned at once instead of charging the client for nothing.
     * يعلّم الدفعة والفاتورة كمدفوعتين. لو الفاتورة انْدفعت قبل بطريقة ثانية (تحويل بنكي، أو دفع ثاني) أو انلغت،
     * نرجّع هالمبلغ فورًا بدل ما ينخصم العميل على الفاضي.
     */
    private void settle(Payment payment, String brand, String last4) {
        Invoice invoice = payment.getInvoice();
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            log.warn("Duplicate or late payment {} for invoice {}: refunding", payment.getId(), invoice.getNumber());
            gateways.of(payment.getProvider()).refund(payment);
            payment.markFailed("DUPLICATE_REFUNDED", brand, last4);
            return;
        }
        Instant now = Instant.now();
        payment.markPaid(now, brand, last4);
        invoice.markPaid(now);
    }

    private CheckoutPage page(Payment p) {
        Invoice inv = p.getInvoice();
        return new CheckoutPage(p.getId(), p.getMethod(), p.getStatus(), p.getProvider(), p.getAmount(),
                p.getCurrency(), inv.getNumber(), inv.getLines().size(),
                p.getMethod().isInstalments() ? plan(p.getAmount()) : List.of(), p.getFailureReason(),
                p.getCardBrand(), p.getCardLast4(), p.getPaidAt(), p.getCheckoutUrl());
    }

    private PaymentGateway.Urls urls(Payment payment) {
        String page = publicUrl + "/pay/" + payment.getId();
        return new PaymentGateway.Urls(page, page + "?returned=1", page);
    }

    private Payment find(UUID id) {
        return payments.findWithInvoice(id).orElseThrow(() -> ApiException.notFound("payment", id));
    }

    private static ApiException fieldError(String field, String rule) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of(field, rule)));
    }
}
