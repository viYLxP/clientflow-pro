package com.clientflow.portal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.auth.TokenService;
import com.clientflow.auth.AuthController.LoginResponse;
import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.config.AppProperties;
import com.clientflow.contact.Contact;
import com.clientflow.contact.ContactService;
import com.clientflow.portal.PortalDtos.AccessStatus;
import com.clientflow.portal.PortalDtos.InviteInfo;
import com.clientflow.portal.PortalDtos.InviteResponse;
import com.clientflow.portal.PortalDtos.PortalAccess;
import com.clientflow.user.AppUser;
import com.clientflow.user.AppUserRepository;
import com.clientflow.user.CurrentUserService;
import com.clientflow.user.Role;
import com.clientflow.user.UserDtos.UserResponse;

/**
 * Client portal access for a contact: invite, re-send the link (also a password reset), turn off, and accepting
 * an invite from the link.
 * دخول بوابة العملاء لجهة اتصال: الدعوة، وإعادة إرسال الرابط (وهو كمان تغيير كلمة المرور)، والإيقاف، وقبول الدعوة
 * من الرابط.
 *
 * <p>The client's login is the contact's email. The contact's owner or an admin manages access, like the contact
 * itself.</p>
 * <p>دخول العميل هو إيميل جهة الاتصال. ومالك جهة الاتصال أو المدير يدير الدخول، مثل جهة الاتصال نفسها.</p>
 */
@Service
public class PortalAccessService {

    private final AppUserRepository users;
    private final ContactService contacts;
    private final CurrentUserService currentUser;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AppProperties properties;

    public PortalAccessService(AppUserRepository users, ContactService contacts, CurrentUserService currentUser,
                               PasswordEncoder passwordEncoder, TokenService tokens, AppProperties properties) {
        this.users = users;
        this.contacts = contacts;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PortalAccess status(Long contactId) {
        contacts.contact(contactId);
        return access(users.findByContactId(contactId));
    }

    /**
     * Creates the client login (if needed) and a fresh one-time link. Any older link stops working.
     * ينشئ دخول العميل (لو ما فيه) ورابط جديد لمرة وحدة. وأي رابط قديم يوقف.
     */
    @Transactional
    public InviteResponse invite(Long contactId) {
        Contact contact = contacts.contact(contactId);
        currentUser.requireOwnerOrAdmin(contact.getOwner());
        if (contact.getEmail() == null) {
            throw new ApiException(ErrorCode.PORTAL_NEEDS_EMAIL);
        }
        if (contact.getCompany() == null) {
            throw new ApiException(ErrorCode.PORTAL_NEEDS_COMPANY);
        }
        String login = contact.getEmail().toLowerCase();
        // The email may belong to a staff member or another client's login. / الإيميل ممكن يكون لموظف أو لدخول عميل ثاني.
        users.findByUsernameIgnoreCase(login)
                .filter(other -> other.getContact() == null || !other.getContact().getId().equals(contactId))
                .ifPresent(other -> {
                    throw new ApiException(ErrorCode.USERNAME_EXISTS);
                });
        AppUser user = users.findByContactId(contactId).orElseGet(() -> {
            // Nobody knows this password; the client sets their own from the link. / ما أحد يعرف هالكلمة؛ العميل يختار كلمته من الرابط.
            AppUser created = new AppUser(login, contact.getFullName(),
                    passwordEncoder.encode(UUID.randomUUID().toString()), Role.CLIENT);
            created.setContact(contact);
            created.setActive(false);
            return created;
        });
        // Keep the login in step with the contact's current email and name. / نخلي الدخول مطابق لإيميل واسم جهة الاتصال الحالي.
        user.setUsername(login);
        user.setFullName(contact.getFullName());
        String token = InviteTokens.newToken();
        user.setInvite(InviteTokens.hash(token), Instant.now().plus(properties.portal().inviteValidity()));
        users.save(user);
        // A fragment (#...) is never sent to a server, so the token stays out of web server logs.
        // الجزء بعد # ما ينرسل للخادم أبدًا، فالرمز ما يطلع في سجلات خادم الويب.
        String url = properties.payments().publicUrl().replaceAll("/+$", "") + "/activate#" + token;
        return new InviteResponse(access(Optional.of(user)), url);
    }

    @Transactional
    public PortalAccess disable(Long contactId) {
        Contact contact = contacts.contact(contactId);
        currentUser.requireOwnerOrAdmin(contact.getOwner());
        AppUser user = users.findByContactId(contactId)
                .orElseThrow(() -> ApiException.notFound("portalAccess", contactId));
        // Signed-in clients are locked out at their next request (SecurityConfig reloads the account each time).
        // العميل المسجّل ينقفل عليه عند طلبه الجاي (SecurityConfig يعيد تحميل الحساب كل مرة).
        user.setActive(false);
        user.setInvite(null, null);
        return access(Optional.of(user));
    }

    @Transactional(readOnly = true)
    public InviteInfo check(String token) {
        AppUser user = invited(token);
        Contact contact = user.getContact();
        return new InviteInfo(user.getFullName(), user.getUsername(),
                contact.getCompany() == null ? null : contact.getCompany().getName());
    }

    /**
     * Sets the client's password from the link, activates the login and signs them in.
     * يحفظ كلمة مرور العميل من الرابط، ويفعّل الدخول، ويسجّل دخوله.
     */
    @Transactional
    public LoginResponse accept(String token, String password) {
        AppUser user = invited(token);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);
        user.setInvite(null, null);
        TokenService.IssuedToken issued = tokens.issue(user);
        return new LoginResponse(issued.value(), issued.expiresAt(), UserResponse.from(user));
    }

    // A wrong, used or expired link all give the same answer. / الرابط الغلط أو المستخدم أو المنتهي كلهم نفس الرد.
    private AppUser invited(String token) {
        return users.findByInviteTokenHash(InviteTokens.hash(token.trim()))
                .filter(u -> u.getRole() == Role.CLIENT)
                .filter(u -> u.getInviteExpiresAt() != null && u.getInviteExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVITE_INVALID));
    }

    private static PortalAccess access(Optional<AppUser> user) {
        if (user.isEmpty()) {
            return new PortalAccess(AccessStatus.NONE, null, null);
        }
        AppUser u = user.get();
        AccessStatus status = u.isActive() ? AccessStatus.ACTIVE
                : u.getInviteTokenHash() != null ? AccessStatus.INVITED : AccessStatus.DISABLED;
        return new PortalAccess(status, u.getUsername(), u.getInviteTokenHash() == null ? null : u.getInviteExpiresAt());
    }
}
