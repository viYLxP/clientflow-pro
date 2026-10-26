package com.clientflow.deal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.company.Company;
import com.clientflow.contact.Contact;
import com.clientflow.user.AppUser;

/**
 * A sales opportunity moving through the pipeline.
 * فرصة بيع تتحرك عبر مراحل خط المبيعات.
 *
 * <p>The stage only changes through {@link #moveTo}, which keeps closedAt and lostReason consistent with the
 * stage (the database CHECK constraints enforce the same rule).</p>
 * <p>المرحلة تتغير فقط عبر {@link #moveTo}، اللي يخلي closedAt و lostReason متوافقة مع المرحلة
 * (وقيود CHECK في قاعدة البيانات تفرض نفس القاعدة).</p>
 */
@Entity
@Table(name = "deal")
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealStage stage = DealStage.LEAD;

    // Deal value in SAR, 2 decimals. / قيمة الصفقة بالريال، بمنزلتين عشريتين.
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Deal() {
    }

    public Deal(String title, Company company, AppUser owner) {
        this.title = title;
        this.company = company;
        this.owner = owner;
    }

    /**
     * Moves the deal to a new stage and returns the previous one.
     * ينقل الصفقة لمرحلة جديدة ويرجّع المرحلة السابقة.
     *
     * Rules / القواعد:
     * - Closing (WON/LOST) stamps closedAt; LOST needs a reason. / الإغلاق يسجّل closedAt، والخسارة تحتاج سبب.
     * - Moving a closed deal back to an open stage "reopens" it and clears closedAt and the reason.
     *   / إرجاع صفقة مغلقة لمرحلة مفتوحة "يعيد فتحها" ويمسح closedAt والسبب.
     * - Moving to the same stage is refused, so the history never records a change that did not happen.
     *   / النقل لنفس المرحلة مرفوض، حتى السجل ما يسجّل تغيير ما صار.
     */
    public DealStage moveTo(DealStage target, String reason, Instant now) {
        if (target == stage) {
            throw new ApiException(ErrorCode.INVALID_STAGE, Map.of("stage", stage.name()));
        }
        DealStage previous = stage;
        stage = target;
        if (target.isClosed()) {
            closedAt = now;
            lostReason = target == DealStage.LOST ? reason : null;
        } else {
            closedAt = null;
            lostReason = null;
        }
        return previous;
    }

    /**
     * Expected value for forecasting: amount × stage probability.
     * القيمة المتوقعة للتوقعات: القيمة × احتمال المرحلة.
     */
    public BigDecimal weightedAmount() {
        return amount.multiply(BigDecimal.valueOf(stage.probability()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Contact getContact() { return contact; }
    public void setContact(Contact contact) { this.contact = contact; }
    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public DealStage getStage() { return stage; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getExpectedCloseDate() { return expectedCloseDate; }
    public void setExpectedCloseDate(LocalDate expectedCloseDate) { this.expectedCloseDate = expectedCloseDate; }
    public String getLostReason() { return lostReason; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
