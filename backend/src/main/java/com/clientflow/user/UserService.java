package com.clientflow.user;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.common.ApiException;
import com.clientflow.common.CurrentUser;
import com.clientflow.common.ErrorCode;
import com.clientflow.user.UserDtos.CreateUserRequest;
import com.clientflow.user.UserDtos.UpdateUserRequest;
import com.clientflow.user.UserDtos.UserResponse;

/**
 * Business rules for managing user accounts (admin only).
 * قواعد إدارة حسابات المستخدمين (للمدير فقط).
 */
@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        // Staff accounts only; client logins are managed from their contact page.
        // حسابات الموظفين بس؛ ودخول العملاء ينْدار من صفحة جهة الاتصال.
        return users.findAll(Sort.by("username")).stream()
                .filter(u -> u.getRole().isStaff())
                .map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse current() {
        return UserResponse.from(findByUsername(CurrentUser.username()));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        // Check first to return a clear error code instead of a raw database constraint error.
        // نتحقق أولًا حتى نرجّع رمز خطأ واضح بدل خطأ قيد قاعدة البيانات.
        requireStaffRole(request.role());
        if (users.existsByUsernameIgnoreCase(request.username())) {
            throw new ApiException(ErrorCode.USERNAME_EXISTS);
        }
        AppUser user = new AppUser(request.username().trim(), request.fullName().trim(),
                passwordEncoder.encode(request.password()), request.role());
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        AppUser user = staff(id);
        requireStaffRole(request.role());
        // An admin cannot disable or demote themselves; otherwise they could lock everyone out.
        // المدير لا يقدر يوقف نفسه أو ينزّل صلاحيته، وإلا ممكن يقفل النظام على الجميع.
        boolean isSelf = user.getUsername().equalsIgnoreCase(CurrentUser.username());
        if (isSelf && (!request.active() || request.role() != user.getRole())) {
            throw new ApiException(ErrorCode.CANNOT_MODIFY_SELF);
        }
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setActive(request.active());
        // No save() call needed: the entity is managed, and JPA writes changes when the transaction commits.
        // لا نحتاج save(): الكيان مُدار، وJPA يحفظ التغييرات تلقائيًا عند نهاية المعاملة.
        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        AppUser user = staff(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }

    // A client login is not a staff account, so /api/users does not see it. / دخول العميل مو حساب موظف، فـ /api/users ما يشوفه.
    private AppUser staff(Long id) {
        return users.findById(id)
                .filter(u -> u.getRole().isStaff())
                .orElseThrow(() -> ApiException.notFound("user", id));
    }

    // Client accounts are created only through a contact's portal invite. / حسابات العملاء تنشأ بس من دعوة البوابة لجهة الاتصال.
    private static void requireStaffRole(Role role) {
        if (!role.isStaff()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("fields", Map.of("role", "Staff")));
        }
    }

    private AppUser findByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> ApiException.notFound("user", username));
    }
}
