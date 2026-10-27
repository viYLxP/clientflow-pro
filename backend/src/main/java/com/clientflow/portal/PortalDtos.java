package com.clientflow.portal;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.clientflow.contact.ContactDtos.CompanyRef;
import com.clientflow.payment.Payment;

/**
 * Request/response shapes for the client portal and portal invites.
 * أشكال الطلب والرد لبوابة العملاء ودعواتها.
 */
public final class PortalDtos {

    private PortalDtos() {
    }

    /**
     * NONE: never invited. INVITED: link sent, not used yet (see inviteExpiresAt). ACTIVE: can sign in.
     * DISABLED: access was turned off.
     * NONE: ما انْدعى. INVITED: انرسل الرابط وما استخدمه (شوف inviteExpiresAt). ACTIVE: يقدر يدخل. DISABLED: انقفل دخوله.
     */
    public enum AccessStatus { NONE, INVITED, ACTIVE, DISABLED }

    public record PortalAccess(AccessStatus status, String login, Instant inviteExpiresAt) {
    }

    /**
     * The link is shown once, to the staff member who created it, to send to the client (by email or WhatsApp).
     * The system does not send emails itself.
     * الرابط ينعرض مرة وحدة للموظف اللي سواه، عشان يرسله للعميل (بالإيميل أو الواتساب). النظام ما يرسل إيميلات بنفسه.
     */
    public record InviteResponse(PortalAccess access, String inviteUrl) {
    }

    public record TokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    // Who the link is for, so the page can greet them before they choose a password. / لمين الرابط، حتى الصفحة ترحّب فيه قبل ما يختار كلمة المرور.
    public record InviteInfo(String fullName, String login, String companyName) {
    }

    public record AcceptInviteRequest(@NotBlank @Size(max = 100) String token,
                                      @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record PortalMe(String fullName, String email, String phone, String jobTitle, CompanyRef company) {
    }

    public record ChangePasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record PortalPayRequest(@NotNull Payment.Method method) {
    }
}
