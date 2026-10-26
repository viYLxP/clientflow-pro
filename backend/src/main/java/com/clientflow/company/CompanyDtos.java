package com.clientflow.company;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.clientflow.user.UserRef;

public final class CompanyDtos {

    // Digits, spaces, dashes, parentheses and an optional leading "+", 7 to 20 characters (e.g. "+966 11 400 1122").
    // The leading "^$|" accepts an empty string, because these fields are optional.
    // أرقام ومسافات وشرطات وأقواس مع "+" اختيارية بالبداية، من 7 إلى 20 خانة (مثل "+966 11 400 1122").
    // "^$|" في البداية يقبل النص الفاضي، لأن هذي الحقول اختيارية.
    public static final String PHONE_REGEX = "^$|^\\+?[0-9 ()-]{7,20}$";
    // http(s) URL or a bare domain such as "example.sa". / رابط http(s) أو نطاق مثل "example.sa".
    public static final String WEBSITE_REGEX = "^$|^(https?://)?[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(/\\S*)?$";

    private CompanyDtos() {
    }

    /**
     * Create/update body. {@code version} is required on update and ignored on create.
     * جسم الإنشاء/التعديل. {@code version} مطلوب في التعديل ويُتجاهل في الإنشاء.
     */
    public record CompanyRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 80) String industry,
            @Size(max = 80) String city,
            @Size(max = 30) @Pattern(regexp = PHONE_REGEX) String phone,
            @Size(max = 150) @Email String email,
            @Size(max = 200) @Pattern(regexp = WEBSITE_REGEX) String website,
            @Size(max = 2000) String notes,
            Long ownerId,
            Long version) {
    }

    public record CompanyResponse(Long id, String name, String industry, String city, String phone, String email,
                                  String website, String notes, UserRef owner, Instant createdAt, Instant updatedAt,
                                  Long version) {

        public static CompanyResponse from(Company c) {
            return new CompanyResponse(c.getId(), c.getName(), c.getIndustry(), c.getCity(), c.getPhone(),
                    c.getEmail(), c.getWebsite(), c.getNotes(), UserRef.of(c.getOwner()), c.getCreatedAt(),
                    c.getUpdatedAt(), c.getVersion());
        }
    }

    /**
     * Company page: the company plus summary numbers for its header.
     * صفحة الشركة: بيانات الشركة مع أرقام ملخصة لرأس الصفحة.
     */
    public record CompanyDetail(CompanyResponse company, long contactCount, long openDealCount,
                                BigDecimal openDealAmount, BigDecimal wonAmount) {
    }
}
