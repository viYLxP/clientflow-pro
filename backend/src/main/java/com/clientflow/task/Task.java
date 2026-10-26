package com.clientflow.task;

import java.time.Instant;
import java.time.LocalDate;

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

import com.clientflow.company.Company;
import com.clientflow.contact.Contact;
import com.clientflow.deal.Deal;
import com.clientflow.user.AppUser;

/**
 * A follow-up to do by a due date (call back, send a proposal...).
 * متابعة لازم تنعمل قبل تاريخ معيّن (اتصال، إرسال عرض...).
 *
 * <p>The status only changes through {@link #complete} and {@link #reopen}, which keep completedAt in step
 * with it (the database CHECK constraint task_done_has_date enforces the same rule).</p>
 * <p>الحالة تتغير فقط عبر {@link #complete} و {@link #reopen}، اللي يخلون completedAt متوافق معها
 * (وقيد task_done_has_date في قاعدة البيانات يفرض نفس القاعدة).</p>
 */
@Entity
@Table(name = "task")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskStatus status = TaskStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", updatable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Task() {
    }

    public Task(String title, LocalDate dueDate, AppUser assignee, AppUser createdBy) {
        this.title = title;
        this.dueDate = dueDate;
        this.assignee = assignee;
        this.createdBy = createdBy;
    }

    /** Marks the task done. Completing a done task is a no-op. / يعلّم المهمة كمنجزة. وإنجاز مهمة منجزة ما يغيّر شي. */
    public void complete(Instant now) {
        if (status == TaskStatus.OPEN) {
            status = TaskStatus.DONE;
            completedAt = now;
        }
    }

    public void reopen() {
        status = TaskStatus.OPEN;
        completedAt = null;
    }

    /**
     * Overdue = still open and the due date is before today (in the business time zone).
     * متأخرة = لسا مفتوحة وتاريخ الاستحقاق قبل اليوم (بتوقيت النشاط).
     */
    public boolean isOverdue(LocalDate today) {
        return status == TaskStatus.OPEN && dueDate.isBefore(today);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public TaskStatus getStatus() { return status; }
    public AppUser getAssignee() { return assignee; }
    public void setAssignee(AppUser assignee) { this.assignee = assignee; }
    public AppUser getCreatedBy() { return createdBy; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Deal getDeal() { return deal; }
    public void setDeal(Deal deal) { this.deal = deal; }
    public Contact getContact() { return contact; }
    public void setContact(Contact contact) { this.contact = contact; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
}
