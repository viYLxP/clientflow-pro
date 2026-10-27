package com.clientflow.inquiry;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.clientflow.company.CompanyDtos;
import com.clientflow.deal.DealDtos.ContactRef;
import com.clientflow.user.UserRef;

/**
 * Request/response shapes for inquiries.
 * أشكال الطلب والرد للاستفسارات.
 */
public final class InquiryDtos {

    private InquiryDtos() {
    }

    /**
     * The public form. {@code website} is a hidden "honeypot" field: people never see it, simple spam bots fill it.
     * النموذج العام. {@code website} حقل مخفي (مصيدة): الناس ما يشوفونه، وروبوتات الإزعاج البسيطة تعبّيه.
     */
    public record InquiryRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 150) String companyName,
            @NotBlank @Size(max = 150) @Email String email,
            @Size(max = 30) @Pattern(regexp = CompanyDtos.PHONE_REGEX) String phone,
            @NotBlank @Size(min = 10, max = 2000) String message,
            @Size(max = 200) String website) {
    }

    /**
     * Turn an inquiry into a lead. All optional: the owner defaults to the caller, the title to the company name,
     * and the amount to 0 until the rep knows the size.
     * تحويل الاستفسار لصفقة. كلها اختيارية: المالك افتراضيًا المستخدم الحالي، والعنوان اسم الشركة، والقيمة 0 لين يعرف
     * المندوب الحجم.
     */
    public record ConvertRequest(
            Long ownerId,
            @Size(max = 150) String dealTitle,
            @DecimalMin("0.00") @DecimalMax("999999999999.99") @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record DealLink(Long id, String title) {
    }

    public record InquiryResponse(Long id, String name, String companyName, String email, String phone,
                                  String message, InquiryStatus status, DealLink deal, UserRef handledBy,
                                  Instant createdAt, Instant handledAt) {

        static InquiryResponse from(Inquiry i) {
            DealLink deal = i.getDeal() == null ? null : new DealLink(i.getDeal().getId(), i.getDeal().getTitle());
            return new InquiryResponse(i.getId(), i.getName(), i.getCompanyName(), i.getEmail(), i.getPhone(),
                    i.getMessage(), i.getStatus(), deal, UserRef.of(i.getHandledBy()), i.getCreatedAt(),
                    i.getHandledAt());
        }
    }

    /** What converting created, so the UI can open the new deal. / وش انعمل من التحويل، حتى الواجهة تفتح الصفقة الجديدة. */
    public record ConvertResponse(InquiryResponse inquiry, Long dealId, Long companyId, ContactRef contact) {
    }
}
