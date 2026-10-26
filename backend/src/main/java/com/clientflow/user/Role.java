package com.clientflow.user;

/**
 * System roles.
 * أدوار النظام.
 *
 * <p>ADMIN: full access, including deleting data, reassigning owners and managing users.
 * SALES: a sales rep; can view everything and create records, but only edits the deals,
 * companies and tasks they own.</p>
 * <p>ADMIN: صلاحية كاملة تشمل الحذف وتغيير المالك وإدارة المستخدمين.
 * SALES: مندوب مبيعات؛ يشوف كل شي ويضيف سجلات، لكن يعدّل فقط الصفقات والشركات والمهام اللي يملكها.</p>
 */
public enum Role {
    ADMIN,
    SALES
}
