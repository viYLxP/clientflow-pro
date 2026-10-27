package com.clientflow.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.contact.Contact;
import com.clientflow.invoice.Invoice;

/**
 * Small helpers shared by the real gateway adapters.
 * أدوات صغيرة مشتركة بين محوّلات البوابات الحقيقية.
 */
final class GatewaySupport {

    private static final Logger log = LoggerFactory.getLogger(GatewaySupport.class);

    private GatewaySupport() {
    }

    /**
     * Any network or HTTP error becomes one clear API error; the details go to the log, not to the customer.
     * أي خطأ شبكة أو HTTP يصير خطأ API واحد واضح؛ والتفاصيل تروح للسجل، مو للعميل.
     */
    static <T> T call(String gateway, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            log.warn("{} call failed: {}", gateway, e.getMessage());
            throw new ApiException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    // Gateways count money in the smallest unit: 345.00 SAR = 34500 halalas. / البوابات تحسب بالهللة: 345.00 ريال = 34500.
    static int halalas(BigDecimal amount) {
        return amount.movePointRight(2).intValueExact();
    }

    // "345.00", the format Tabby expects. / "345.00"، الصيغة اللي تبيها تابي.
    static String decimal(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // Whether two amounts are the same money (345 and 345.00 are). / هل المبلغين نفس الفلوس (345 و345.00 نفسهم).
    static boolean same(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    /**
     * Who pays, for the instalment companies: the invoice's contact person, else the company itself.
     * مين يدفع، لشركات التقسيط: الشخص الموجّهة له الفاتورة، وإلا الشركة نفسها.
     */
    static Payer payer(Invoice invoice) {
        Contact c = invoice.getContact();
        String name = c != null ? c.getFullName() : invoice.getCompany().getName();
        String email = c != null && c.getEmail() != null ? c.getEmail() : invoice.getCompany().getEmail();
        String phone = c != null && c.getPhone() != null ? c.getPhone() : invoice.getCompany().getPhone();
        String city = invoice.getCompany().getCity() == null ? "Riyadh" : invoice.getCompany().getCity();
        return new Payer(name, email, saudiDigits(phone), city);
    }

    /**
     * @param phone 9665XXXXXXXX (digits only), or null when there is no usable Saudi number
     *              / 9665XXXXXXXX (أرقام بس)، أو null لو ما فيه رقم سعودي صالح
     */
    record Payer(String name, String email, String phone, String city) {

        // "05XXXXXXXX" for Tabby. / "05XXXXXXXX" لتابي.
        String localPhone() {
            return phone == null ? null : "0" + phone.substring(3);
        }

        // "+9665XXXXXXXX" for Tamara. / "+9665XXXXXXXX" لتمارا.
        String internationalPhone() {
            return phone == null ? null : "+" + phone;
        }
    }

    // Accepts "+966 50 111 2233", "0501112233" or "966501112233". / يقبل الصيغ الثلاث للرقم السعودي.
    static String saudiDigits(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0")) {
            digits = "966" + digits.substring(1);
        }
        return digits.matches("^9665\\d{8}$") ? digits : null;
    }
}
