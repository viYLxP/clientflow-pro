package com.clientflow.deal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.clientflow.contact.ContactDtos.CompanyRef;
import com.clientflow.user.UserRef;

public final class DealDtos {

    private DealDtos() {
    }

    /**
     * Create or edit a deal. The stage is set only on create; afterwards it changes through the stage endpoint,
     * so every change is recorded in the history.
     * إنشاء أو تعديل صفقة. المرحلة تنحدد فقط عند الإنشاء؛ بعدها تتغير عن طريق مسار المرحلة،
     * حتى كل تغيير ينسجل في السجل.
     */
    public record DealRequest(
            @NotBlank @Size(max = 150) String title,
            @NotNull Long companyId,
            Long contactId,
            Long ownerId,
            // Up to 999,999,999,999.99 to match NUMERIC(14,2). / لحد 999,999,999,999.99 حتى يطابق NUMERIC(14,2).
            @NotNull @DecimalMin("0.00") @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            LocalDate expectedCloseDate,
            // Only read on create; null means LEAD. Creating a deal directly as WON/LOST is refused.
            // تُقرأ فقط عند الإنشاء؛ null يعني LEAD. وإنشاء صفقة مباشرة كفائزة أو خاسرة مرفوض.
            DealStage stage,
            Long version) {
    }

    /** Move a deal to another stage. / نقل صفقة لمرحلة ثانية. */
    public record StageRequest(
            @NotNull DealStage stage,
            @Size(max = 255) String lostReason,
            @NotNull Long version) {
    }

    public record ContactRef(Long id, String fullName) {
    }

    public record DealResponse(Long id, String title, CompanyRef company, ContactRef contact, UserRef owner,
                               DealStage stage, int probability, BigDecimal amount, BigDecimal weightedAmount,
                               LocalDate expectedCloseDate, String lostReason, Instant closedAt,
                               Instant createdAt, Instant updatedAt, Long version) {

        public static DealResponse from(Deal d) {
            ContactRef contact = d.getContact() == null ? null
                    : new ContactRef(d.getContact().getId(), d.getContact().getFullName());
            return new DealResponse(d.getId(), d.getTitle(),
                    new CompanyRef(d.getCompany().getId(), d.getCompany().getName()), contact,
                    UserRef.of(d.getOwner()), d.getStage(), d.getStage().probability(), d.getAmount(),
                    d.weightedAmount(), d.getExpectedCloseDate(), d.getLostReason(), d.getClosedAt(),
                    d.getCreatedAt(), d.getUpdatedAt(), d.getVersion());
        }
    }

    /**
     * One pipeline column: the totals cover every deal in the stage, while {@code deals} is capped,
     * so a busy column stays fast and the header still shows the true numbers.
     * عمود واحد في اللوحة: الإجماليات تشمل كل صفقات المرحلة، بينما {@code deals} محدودة العدد،
     * فالعمود المزدحم يبقى سريع والعنوان يعرض الأرقام الحقيقية.
     */
    public record BoardColumn(DealStage stage, int probability, long count, BigDecimal amount,
                              List<DealResponse> deals) {
    }

    public record StageChangeResponse(Long id, DealStage fromStage, DealStage toStage, String changedBy,
                                      Instant changedAt) {

        public static StageChangeResponse from(DealStageChange c) {
            return new StageChangeResponse(c.getId(), c.getFromStage(), c.getToStage(), c.getChangedBy(),
                    c.getChangedAt());
        }
    }
}
