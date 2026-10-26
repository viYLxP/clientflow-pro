package com.clientflow.contact;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.clientflow.company.CompanyDtos;
import com.clientflow.user.UserRef;

public final class ContactDtos {

    private ContactDtos() {
    }

    public record ContactRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Size(max = 150) @Email String email,
            @Size(max = 30) @Pattern(regexp = CompanyDtos.PHONE_REGEX) String phone,
            @Size(max = 100) String jobTitle,
            @Size(max = 2000) String notes,
            Long companyId,
            Long ownerId,
            Long version) {
    }

    /** Company reference shown on other records. / مرجع الشركة اللي يظهر على السجلات الثانية. */
    public record CompanyRef(Long id, String name) {
    }

    public record ContactResponse(Long id, String firstName, String lastName, String fullName, String email,
                                  String phone, String jobTitle, String notes, CompanyRef company, UserRef owner,
                                  Instant createdAt, Long version) {

        public static ContactResponse from(Contact c) {
            CompanyRef company = c.getCompany() == null ? null
                    : new CompanyRef(c.getCompany().getId(), c.getCompany().getName());
            return new ContactResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getFullName(), c.getEmail(),
                    c.getPhone(), c.getJobTitle(), c.getNotes(), company, UserRef.of(c.getOwner()),
                    c.getCreatedAt(), c.getVersion());
        }
    }
}
