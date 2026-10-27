package com.clientflow.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.clientflow.invoice.Invoice;

/**
 * One payment attempt for an invoice: online through a gateway, or a bank transfer recorded by staff.
 * محاولة دفع وحدة لفاتورة: أونلاين عن طريق بوابة، أو تحويل بنكي يسجّله الموظف.
 *
 * <p>A failed card attempt stays in the table, so staff can see that the client tried. Only one payment per
 * invoice can be PAID (a partial unique index in the database guarantees it).</p>
 * <p>محاولة البطاقة الفاشلة تبقى في الجدول، حتى يشوف الموظف إن العميل حاول. ودفعة وحدة بس لكل فاتورة تقدر تكون
 * PAID (فهرس فريد جزئي في قاعدة البيانات يضمن هالشي).</p>
 */
@Entity
@Table(name = "payment")
public class Payment {

    // APPLE_PAY goes through the card gateway's own page; TABBY and TAMARA split the amount into 4 instalments
    // (we are paid in full, the client pays the instalment company). BANK_TRANSFER is recorded by staff.
    // APPLE_PAY يمر من صفحة بوابة البطاقات نفسها؛ وTABBY وTAMARA يقسمون المبلغ 4 دفعات (المتجر يستلم المبلغ
    // كامل، والعميل يسدد لشركة التقسيط). وBANK_TRANSFER يسجّله الموظف.
    public enum Method {
        CARD, APPLE_PAY, TABBY, TAMARA, BANK_TRANSFER;

        public boolean isInstalments() {
            return this == TABBY || this == TAMARA;
        }
    }

    public enum Provider { SANDBOX, MOYASAR, STRIPE, TABBY, TAMARA, MANUAL }

    public enum Status { PENDING, PAID, FAILED, REFUNDED }

    // Random, not a sequence: it is part of the payment page link, so it must not be guessable.
    // عشوائي مو متسلسل: هو جزء من رابط صفحة الدفع، فلازم ما ينخمّن.
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    // The gateway's own id (Moyasar invoice, Stripe session, Tabby payment, Tamara order).
    // رقم العملية عند البوابة (فاتورة Moyasar، جلسة Stripe، دفعة تابي، طلب تمارا).
    @Column(name = "provider_ref", length = 200)
    private String providerRef;

    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "SAR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    // Staff username for a bank transfer, the client's login for online payments.
    // اسم الموظف للتحويل البنكي، ودخول العميل للدفع أونلاين.
    @Column(name = "recorded_by", length = 150)
    private String recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    protected Payment() {
    }

    public Payment(Invoice invoice, Method method, Provider provider, BigDecimal amount, String recordedBy,
                   Instant createdAt) {
        this.id = UUID.randomUUID();
        this.invoice = invoice;
        this.method = method;
        this.provider = provider;
        this.amount = amount;
        this.recordedBy = recordedBy;
        this.createdAt = createdAt;
    }

    public void markPaid(Instant at, String brand, String last4) {
        this.status = Status.PAID;
        this.paidAt = at;
        this.failureReason = null;
        this.cardBrand = brand;
        this.cardLast4 = last4;
    }

    public void markFailed(String reason, String brand, String last4) {
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.cardBrand = brand;
        this.cardLast4 = last4;
    }

    public void markRefunded(Instant at) {
        this.status = Status.REFUNDED;
        this.refundedAt = at;
    }

    public UUID getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public Method getMethod() { return method; }
    public Provider getProvider() { return provider; }
    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public String getCardBrand() { return cardBrand; }
    public String getCardLast4() { return cardLast4; }
    public String getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getRefundedAt() { return refundedAt; }
}
