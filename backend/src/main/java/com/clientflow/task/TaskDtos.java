package com.clientflow.task;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.clientflow.contact.ContactDtos.CompanyRef;
import com.clientflow.deal.DealDtos.ContactRef;
import com.clientflow.user.UserRef;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record TaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull LocalDate dueDate,
            @NotNull TaskPriority priority,
            // Null = assign to myself. / null = أكلّف نفسي.
            Long assigneeId,
            Long companyId,
            Long dealId,
            Long contactId,
            Long version) {
    }

    /** Body for complete / reopen: only the version, to catch concurrent edits. / جسم الإنجاز وإعادة الفتح: النسخة فقط لكشف التعديل المتزامن. */
    public record VersionRequest(@NotNull Long version) {
    }

    public record DealRef(Long id, String title) {
    }

    public record TaskResponse(Long id, String title, String description, LocalDate dueDate, TaskPriority priority,
                               TaskStatus status, boolean overdue, UserRef assignee, UserRef createdBy,
                               CompanyRef company, DealRef deal, ContactRef contact, Instant completedAt,
                               Instant createdAt, Long version) {

        public static TaskResponse from(Task t, LocalDate today) {
            return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.getDueDate(), t.getPriority(),
                    t.getStatus(), t.isOverdue(today), UserRef.of(t.getAssignee()), UserRef.of(t.getCreatedBy()),
                    t.getCompany() == null ? null : new CompanyRef(t.getCompany().getId(), t.getCompany().getName()),
                    t.getDeal() == null ? null : new DealRef(t.getDeal().getId(), t.getDeal().getTitle()),
                    t.getContact() == null ? null : new ContactRef(t.getContact().getId(), t.getContact().getFullName()),
                    t.getCompletedAt(), t.getCreatedAt(), t.getVersion());
        }
    }
}
