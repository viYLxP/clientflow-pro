package com.clientflow.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;

/**
 * Who is calling, and may they change a given record.
 * مين المستخدم الحالي، وهل يحق له يعدّل سجل معيّن.
 *
 * <p>Ownership is the core access rule of the CRM: everyone can read, but a sales rep only changes what they own.
 * Admins may change anything. Keeping the rule in one class means every service enforces it the same way.</p>
 * <p>الملكية هي قاعدة الصلاحيات الأساسية في النظام: الكل يقرأ، لكن المندوب يعدّل فقط اللي يملكه،
 * والمدير يعدّل أي شي. وجود القاعدة في كلاس واحد يضمن إن كل الخدمات تطبقها بنفس الطريقة.</p>
 */
@Service
public class CurrentUserService {

    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) {
        this.users = users;
    }

    /**
     * The logged-in user's account. The JWT subject is the username.
     * حساب المستخدم المسجّل. حقل subject في JWT هو اسم المستخدم.
     */
    public AppUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        // A deactivated user may still hold a valid token until it expires; refuse them here.
        // المستخدم المعطّل ممكن يكون معه رمز صالح لين ينتهي، فنرفضه هنا.
        return users.findByUsernameIgnoreCase(auth.getName())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));
    }

    public boolean isAdmin(AppUser user) {
        return user.getRole() == Role.ADMIN;
    }

    /**
     * Fails with NOT_OWNER unless the current user is the owner or an admin.
     * يرمي NOT_OWNER إلا لو المستخدم الحالي هو المالك أو مدير.
     */
    public AppUser requireOwnerOrAdmin(AppUser owner) {
        AppUser me = require();
        if (!isAdmin(me) && !me.getId().equals(owner.getId())) {
            throw new ApiException(ErrorCode.NOT_OWNER);
        }
        return me;
    }

    /**
     * Resolves the owner for a new or edited record.
     * يحدد المالك لسجل جديد أو معدّل.
     *
     * No owner given → the current user. Giving someone else as owner (assigning work) is admin-only, so a rep
     * cannot push records onto colleagues or take over theirs.
     * بدون مالك ← المستخدم الحالي. تعيين شخص ثاني كمالك للمدير فقط، حتى المندوب ما يرمي سجلات على زملائه
     * ولا ياخذ سجلاتهم.
     */
    public AppUser resolveOwner(Long requestedOwnerId, AppUser currentOwner) {
        AppUser me = require();
        if (requestedOwnerId == null) {
            return currentOwner != null ? currentOwner : me;
        }
        if (currentOwner != null && currentOwner.getId().equals(requestedOwnerId)) {
            return currentOwner;
        }
        // A rep may only name themselves (and only on a new record, since they can edit only what they own).
        // المندوب يقدر يختار نفسه فقط (وفقط لسجل جديد، لأنه أصلًا ما يعدّل إلا اللي يملكه).
        if (!isAdmin(me) && !me.getId().equals(requestedOwnerId)) {
            throw new ApiException(ErrorCode.NOT_OWNER);
        }
        return activeUser(requestedOwnerId);
    }

    /**
     * An active user by id, for owners and assignees. Archived (inactive) users cannot receive new work.
     * مستخدم نشط حسب الرقم، للمالكين والمكلّفين. المستخدم المعطّل ما ينعطى شغل جديد.
     */
    public AppUser activeUser(Long id) {
        return users.findById(id)
                .filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.notFound("user", id));
    }
}
