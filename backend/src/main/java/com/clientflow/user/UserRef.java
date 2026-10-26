package com.clientflow.user;

/**
 * The short form of a user shown on other records (owner, assignee, author).
 * الشكل المختصر للمستخدم اللي يظهر على السجلات الثانية (المالك، المكلّف، الكاتب).
 */
public record UserRef(Long id, String fullName) {

    public static UserRef of(AppUser user) {
        return user == null ? null : new UserRef(user.getId(), user.getFullName());
    }
}
