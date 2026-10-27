package com.clientflow.user;

/**
 * System roles.
 * أدوار النظام.
 *
 * <p>ADMIN: full access, including deleting data, reassigning owners and managing users.
 * SALES: a sales rep; can view everything and create records, but only edits the deals,
 * companies and tasks they own.
 * CLIENT: a customer's contact person; signs in to the client portal only, to see and pay their company's invoices.</p>
 * <p>ADMIN: صلاحية كاملة تشمل الحذف وتغيير المالك وإدارة المستخدمين.
 * SALES: مندوب مبيعات؛ يشوف كل شي ويضيف سجلات، لكن يعدّل فقط الصفقات والشركات والمهام اللي يملكها.
 * CLIENT: الشخص المسؤول عند العميل؛ يدخل بوابة العملاء بس، عشان يشوف فواتير شركته ويدفعها.</p>
 */
public enum Role {
    ADMIN,
    SALES,
    CLIENT;

    // Staff work inside the CRM; a client only sees the portal. / الموظفين يشتغلون داخل النظام؛ والعميل يشوف البوابة بس.
    public boolean isStaff() {
        return this != CLIENT;
    }
}
