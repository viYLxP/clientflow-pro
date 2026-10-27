package com.clientflow.user;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A minimal list of active team members (id + name) for owner and assignee pickers.
 * قائمة مختصرة بأعضاء الفريق النشطين (الرقم + الاسم) لقوائم اختيار المالك والمكلّف.
 *
 * <p>Separate from /api/users (admin-only), because every rep needs names but must not see roles or manage accounts.</p>
 * <p>منفصلة عن /api/users (للمدير فقط)، لأن كل مندوب يحتاج الأسماء لكن ما يشوف الأدوار ولا يدير الحسابات.</p>
 */
@RestController
@RequestMapping("/api/team")
public class TeamController {

    public record TeamMember(Long id, String fullName) {
    }

    private final AppUserRepository users;

    public TeamController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping
    public List<TeamMember> list() {
        return users.findAll().stream()
                // Client portal accounts are not team members. / حسابات بوابة العملاء مو من الفريق.
                .filter(u -> u.isActive() && u.getRole().isStaff())
                .sorted(Comparator.comparing(AppUser::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new TeamMember(u.getId(), u.getFullName()))
                .toList();
    }
}
