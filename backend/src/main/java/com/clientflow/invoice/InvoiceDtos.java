package com.clientflow.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.clientflow.contact.ContactDtos.CompanyRef;
import com.clientflow.deal.DealDtos.ContactRef;
import com.clientflow.payment.PaymentDtos.PaymentSummary;
import com.clientflow.payment.PaymentDtos.PaymentView;
import com.clientflow.user.UserRef;

/**
 * Request/response shapes for invoices.
 * أشكال الطلب والرد للفواتير.
 */
public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    // Prices are before VAT; the server adds VAT and computes every total. / الأسعار قبل الضريبة؛ والخادم يضيفها ويحسب كل المجاميع.
    public record LineRequest(
            @NotBlank @Size(max = 300) String description,
            @Min(1) @Max(100000) int quantity,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {
    }

    /**
     * Create or edit a draft. {@code version} is required on edit and ignored on create.
     * إنشاء أو تعديل مسودة. {@code version} مطلوب في التعديل ويُتجاهل في الإنشاء.
     */
    public record InvoiceRequest(
            @NotNull Long companyId,
            Long contactId,
            Long dealId,
            @NotNull LocalDate dueDate,
            @Size(max = 1000) String notes,
            @NotEmpty @Size(max = 50) List<@Valid @NotNull LineRequest> lines,
            Long version) {
    }

    public record VersionRequest(@NotNull Long version) {
    }

    public record VoidRequest(@NotBlank @Size(max = 255) String reason, @NotNull Long version) {
    }

    // The bank's transfer reference, so the accountant can match the statement. / مرجع التحويل من البنك، حتى المحاسب يطابق الكشف.
    public record TransferRequest(@Size(max = 100) String reference, @NotNull Long version) {
    }

    public record DealRef(Long id, String title) {
    }

    public record LineResponse(int position, String description, int quantity, BigDecimal unitPrice,
                               BigDecimal lineTotal) {

        static LineResponse from(InvoiceLine l) {
            return new LineResponse(l.getPosition(), l.getDescription(), l.getQuantity(), l.getUnitPrice(),
                    l.getLineTotal());
        }
    }

    /** One row of an invoice list. / صف واحد في قائمة الفواتير. */
    public record InvoiceSummary(Long id, String number, CompanyRef company, ContactRef contact,
                                 InvoiceStatus status, boolean overdue, LocalDate issueDate, LocalDate dueDate,
                                 BigDecimal total, Instant paidAt) {

        public static InvoiceSummary from(Invoice i, LocalDate today) {
            return new InvoiceSummary(i.getId(), i.getNumber(), companyOf(i), contactOf(i), i.getStatus(),
                    i.isOverdue(today), i.getIssueDate(), i.getDueDate(), i.getTotal(), i.getPaidAt());
        }
    }

    /**
     * The invoice itself, as staff and the client see it.
     * الفاتورة نفسها، مثل ما يشوفها الموظف والعميل.
     */
    public record InvoiceResponse(Long id, String number, CompanyRef company, ContactRef contact, DealRef deal,
                                  InvoiceStatus status, boolean overdue, LocalDate issueDate, LocalDate dueDate,
                                  BigDecimal subtotal, BigDecimal vat, BigDecimal total, String notes,
                                  List<LineResponse> lines, Instant issuedAt, Instant paidAt, Instant voidedAt,
                                  String voidReason, Instant createdAt, Long version) {

        public static InvoiceResponse from(Invoice i, LocalDate today) {
            DealRef deal = i.getDeal() == null ? null : new DealRef(i.getDeal().getId(), i.getDeal().getTitle());
            return new InvoiceResponse(i.getId(), i.getNumber(), companyOf(i), contactOf(i), deal, i.getStatus(),
                    i.isOverdue(today), i.getIssueDate(), i.getDueDate(), i.getSubtotal(), i.getVat(), i.getTotal(),
                    i.getNotes(), i.getLines().stream().map(LineResponse::from).toList(), i.getIssuedAt(),
                    i.getPaidAt(), i.getVoidedAt(), i.getVoidReason(), i.getCreatedAt(), i.getVersion());
        }
    }

    /**
     * The staff invoice page: the invoice, who wrote it, may I change it, and every payment attempt.
     * صفحة الفاتورة للموظف: الفاتورة، ومين كتبها، وهل أقدر أعدّلها، وكل محاولات الدفع.
     */
    public record InvoiceDetail(InvoiceResponse invoice, UserRef createdBy, boolean canManage,
                                PaymentSummary payment, List<PaymentView> payments) {
    }

    /** The client's view: the invoice and how to pay it, nothing internal. / عرض العميل: الفاتورة وطريقة دفعها، بدون أي شي داخلي. */
    public record PortalInvoice(InvoiceResponse invoice, PaymentSummary payment) {
    }

    private static CompanyRef companyOf(Invoice i) {
        return new CompanyRef(i.getCompany().getId(), i.getCompany().getName());
    }

    private static ContactRef contactOf(Invoice i) {
        return i.getContact() == null ? null : new ContactRef(i.getContact().getId(), i.getContact().getFullName());
    }
}
