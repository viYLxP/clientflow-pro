package com.clientflow.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.company.Company;
import com.clientflow.contact.Contact;
import com.clientflow.deal.Deal;
import com.clientflow.user.AppUser;

/**
 * An invoice to a company, optionally for a deal and addressed to one of its contacts.
 * فاتورة لشركة، واختياريًا لصفقة وموجّهة لوحدة من جهات اتصالها.
 *
 * <p>The status only changes through {@link #issue}, {@link #markPaid} and {@link #voidWith}, which keep the dates
 * and the reason consistent (the database CHECK constraints enforce the same rules). Totals are always computed
 * here from the lines, never taken from the browser.</p>
 * <p>الحالة تتغير فقط عبر {@link #issue} و{@link #markPaid} و{@link #voidWith}، اللي يخلون التواريخ والسبب
 * متوافقة (وقيود CHECK في قاعدة البيانات تفرض نفس القواعد). والمجاميع تنحسب هنا من الأسطر دايم، أبدًا من المتصفح.</p>
 */
@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    // The person the invoice is addressed to (shown on the invoice). / الشخص الموجّهة له الفاتورة (يظهر عليها).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal vat = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason")
    private String voidReason;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<InvoiceLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Invoice() {
    }

    public Invoice(String number, Company company, AppUser createdBy) {
        this.number = number;
        this.company = company;
        this.createdBy = createdBy;
    }

    /**
     * One line per (description, quantity, unit price), replacing the old lines, then recomputes the totals.
     * VAT is rounded half-up to the halala on the subtotal, like a printed tax invoice.
     * سطر لكل (وصف، كمية، سعر وحدة) بدل الأسطر القديمة، وبعدها يعيد حساب المجاميع.
     * الضريبة تتقرّب لأقرب هللة على المجموع قبل الضريبة، مثل الفاتورة الضريبية المطبوعة.
     */
    public void replaceLines(List<LineInput> inputs, BigDecimal vatRate) {
        requireDraft();
        lines.clear();
        int position = 0;
        for (LineInput in : inputs) {
            lines.add(new InvoiceLine(this, position++, in.description(), in.quantity(), in.unitPrice()));
        }
        subtotal = lines.stream().map(InvoiceLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        vat = subtotal.multiply(vatRate).setScale(2, RoundingMode.HALF_UP);
        total = subtotal.add(vat);
    }

    public record LineInput(String description, int quantity, BigDecimal unitPrice) {
    }

    void issue(LocalDate today, Instant now) {
        requireDraft();
        status = InvoiceStatus.ISSUED;
        issueDate = today;
        issuedAt = now;
    }

    public void markPaid(Instant at) {
        if (status != InvoiceStatus.ISSUED) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        status = InvoiceStatus.PAID;
        paidAt = at;
    }

    void voidWith(String reason, Instant at) {
        if (status != InvoiceStatus.ISSUED && status != InvoiceStatus.PAID) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", status.name()));
        }
        status = InvoiceStatus.VOID;
        voidReason = reason;
        voidedAt = at;
    }

    void requireDraft() {
        if (status != InvoiceStatus.DRAFT) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", status.name()));
        }
    }

    // Issued and past its due date, in the business time zone. / صادرة وفات تاريخ استحقاقها، بتوقيت النشاط.
    public boolean isOverdue(LocalDate today) {
        return status == InvoiceStatus.ISSUED && dueDate.isBefore(today);
    }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Contact getContact() { return contact; }
    public void setContact(Contact contact) { this.contact = contact; }
    public Deal getDeal() { return deal; }
    public void setDeal(Deal deal) { this.deal = deal; }
    public InvoiceStatus getStatus() { return status; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getVat() { return vat; }
    public BigDecimal getTotal() { return total; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getVoidedAt() { return voidedAt; }
    public String getVoidReason() { return voidReason; }
    public List<InvoiceLine> getLines() { return lines; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
