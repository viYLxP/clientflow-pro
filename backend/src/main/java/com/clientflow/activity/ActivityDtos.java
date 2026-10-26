package com.clientflow.activity;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.clientflow.contact.ContactDtos.CompanyRef;
import com.clientflow.deal.DealDtos.ContactRef;
import com.clientflow.task.TaskDtos.DealRef;
import com.clientflow.user.UserRef;

public final class ActivityDtos {

    private ActivityDtos() {
    }

    /**
     * At least one of companyId / dealId / contactId (with a company) is needed; see RecordLinks.
     * لازم واحد على الأقل من companyId / dealId / contactId (له شركة)؛ شوف RecordLinks.
     */
    public record ActivityRequest(
            @NotNull ActivityType type,
            @NotBlank @Size(max = 2000) String body,
            // Null = now. / null = الحين.
            Instant occurredAt,
            Long companyId,
            Long dealId,
            Long contactId) {
    }

    public record ActivityResponse(Long id, ActivityType type, String body, Instant occurredAt, UserRef author,
                                   CompanyRef company, DealRef deal, ContactRef contact, Instant createdAt) {

        public static ActivityResponse from(Activity a) {
            return new ActivityResponse(a.getId(), a.getType(), a.getBody(), a.getOccurredAt(),
                    UserRef.of(a.getAuthor()),
                    new CompanyRef(a.getCompany().getId(), a.getCompany().getName()),
                    a.getDeal() == null ? null : new DealRef(a.getDeal().getId(), a.getDeal().getTitle()),
                    a.getContact() == null ? null : new ContactRef(a.getContact().getId(), a.getContact().getFullName()),
                    a.getCreatedAt());
        }
    }
}
