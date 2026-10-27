package com.clientflow.inquiry;

import java.time.Instant;
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

import org.hibernate.annotations.CreationTimestamp;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.deal.Deal;
import com.clientflow.user.AppUser;

/**
 * A message a visitor sent through the public "contact sales" form.
 * رسالة أرسلها زائر من نموذج "تواصل مع المبيعات" العام.
 */
@Entity
@Table(name = "inquiry")
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "company_name", length = 150)
    private String companyName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InquiryStatus status = InquiryStatus.NEW;

    // The lead created from it. / الصفقة اللي انعملت منه.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private AppUser handledBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "handled_at")
    private Instant handledAt;

    protected Inquiry() {
    }

    public Inquiry(String name, String companyName, String email, String phone, String message) {
        this.name = name;
        this.companyName = companyName;
        this.email = email;
        this.phone = phone;
        this.message = message;
    }

    void convert(Deal deal, AppUser by, Instant at) {
        requireNew();
        this.status = InquiryStatus.CONVERTED;
        this.deal = deal;
        this.handledBy = by;
        this.handledAt = at;
    }

    void dismiss(AppUser by, Instant at) {
        requireNew();
        this.status = InquiryStatus.DISMISSED;
        this.handledBy = by;
        this.handledAt = at;
    }

    // Each message is handled once, so two reps cannot both turn it into a deal. / كل رسالة تنعالج مرة، فمندوبين ما يحوّلونها صفقتين.
    private void requireNew() {
        if (status != InquiryStatus.NEW) {
            throw new ApiException(ErrorCode.INVALID_STATUS, Map.of("status", status.name()));
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCompanyName() { return companyName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getMessage() { return message; }
    public InquiryStatus getStatus() { return status; }
    public Deal getDeal() { return deal; }
    public AppUser getHandledBy() { return handledBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getHandledAt() { return handledAt; }
}
