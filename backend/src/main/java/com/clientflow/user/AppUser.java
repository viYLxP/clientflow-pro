package com.clientflow.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.clientflow.contact.Contact;

/**
 * A user account that can log in to the system.
 * حساب مستخدم يقدر يسجّل دخول للنظام.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Staff pick a short name; a client signs in with their email. / الموظف يختار اسم قصير؛ والعميل يدخل بإيميله.
    @Column(nullable = false, unique = true, length = 150)
    private String username;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    // Only the BCrypt hash is stored, never the plain password.
    // نخزّن ناتج BCrypt فقط، ولا نخزّن كلمة المرور الأصلية أبدًا.
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // Stored as text ("ADMIN") so adding a new role later does not shift existing values.
    // نخزّنها كنص ("ADMIN") حتى لا تتغير القيم القديمة لو أضفنا دورًا جديدًا لاحقًا.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // Disabled users cannot log in, but their history (deals, notes, payments) stays intact.
    // المستخدم الموقوف لا يقدر يدخل، لكن سجله (الصفقات والملاحظات والمدفوعات) يبقى محفوظًا.
    @Column(nullable = false)
    private boolean active = true;

    // Set only for CLIENT accounts: the contact person whose company's invoices they see.
    // موجود بس لحسابات CLIENT: جهة الاتصال اللي يشوف فواتير شركتها.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    // SHA-256 of the one-time invite link token, and when it stops working. / SHA-256 لرمز رابط الدعوة، ومتى ينتهي.
    @Column(name = "invite_token_hash", length = 64)
    private String inviteTokenHash;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
        // Required by JPA.
        // مطلوب من JPA.
    }

    public AppUser(String username, String fullName, String passwordHash, Role role) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public String getInviteTokenHash() {
        return inviteTokenHash;
    }

    public Instant getInviteExpiresAt() {
        return inviteExpiresAt;
    }

    public void setInvite(String tokenHash, Instant expiresAt) {
        this.inviteTokenHash = tokenHash;
        this.inviteExpiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
