package com.clientflow.activity;

import java.time.Instant;

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

import com.clientflow.company.Company;
import com.clientflow.contact.Contact;
import com.clientflow.deal.Deal;
import com.clientflow.user.AppUser;

/**
 * One logged interaction with a customer. Activities are a log, so they are not edited after the fact,
 * only added or (by their author or an admin) deleted.
 * تواصل واحد مسجّل مع عميل. السجل ما يتعدّل بعدين، فقط يُضاف أو ينحذف (من كاتبه أو المدير).
 */
@Entity
@Table(name = "activity")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActivityType type;

    @Column(nullable = false, length = 2000)
    private String body;

    // When it happened (may be earlier than when it was logged). / متى صار (ممكن يكون قبل وقت التسجيل).
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private AppUser author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Activity() {
    }

    public Activity(ActivityType type, String body, Instant occurredAt, AppUser author, Company company, Deal deal,
                    Contact contact) {
        this.type = type;
        this.body = body;
        this.occurredAt = occurredAt;
        this.author = author;
        this.company = company;
        this.deal = deal;
        this.contact = contact;
    }

    public Long getId() { return id; }
    public ActivityType getType() { return type; }
    public String getBody() { return body; }
    public Instant getOccurredAt() { return occurredAt; }
    public AppUser getAuthor() { return author; }
    public Company getCompany() { return company; }
    public Deal getDeal() { return deal; }
    public Contact getContact() { return contact; }
    public Instant getCreatedAt() { return createdAt; }
}
