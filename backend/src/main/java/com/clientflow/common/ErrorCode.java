package com.clientflow.common;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned by the API.
 * رموز أخطاء ثابتة يرجّعها الـ API ويفهمها البرنامج.
 *
 * <p>The frontend translates these codes into Arabic or English, so the API never returns
 * user-facing text. This keeps server logic independent of the display language.</p>
 * <p>الواجهة الأمامية تترجم هذه الرموز إلى العربية أو الإنجليزية، فلا يرجّع الـ API أي نص
 * موجّه للمستخدم. هذا يفصل منطق الخادم عن لغة العرض.</p>
 */
public enum ErrorCode {
    // Request data is invalid (missing field, negative number, malformed JSON...).
    // بيانات الطلب غير صحيحة (حقل ناقص، رقم سالب، JSON تالف...).
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    // Wrong username/password, or the account is disabled.
    // اسم المستخدم أو كلمة المرور خطأ، أو الحساب موقوف.
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    // Logged in, but the role does not allow this action.
    // المستخدم مسجّل دخول لكن صلاحيته لا تسمح بالعملية.
    FORBIDDEN(HttpStatus.FORBIDDEN),
    // The requested record does not exist.
    // السجل المطلوب غير موجود.
    NOT_FOUND(HttpStatus.NOT_FOUND),
    // A sales rep tried to change a record owned by someone else (only the owner or an admin may).
    // مندوب حاول يعدّل سجل يملكه شخص ثاني (فقط المالك أو المدير يقدر).
    NOT_OWNER(HttpStatus.FORBIDDEN),
    // The username is already taken.
    // اسم المستخدم محجوز.
    USERNAME_EXISTS(HttpStatus.CONFLICT),
    // Duplicate name (for example a company name).
    // الاسم مكرر (مثل اسم شركة).
    NAME_EXISTS(HttpStatus.CONFLICT),
    // Cannot delete because other records reference it (e.g. a company that still has deals).
    // لا يمكن الحذف لأن سجلات أخرى مرتبطة به (مثل شركة عندها صفقات).
    IN_USE(HttpStatus.CONFLICT),
    // Action not allowed in the deal's current stage (e.g. editing a won deal without reopening it).
    // العملية غير مسموحة في مرحلة الصفقة الحالية (مثل تعديل صفقة رابحة بدون إعادة فتحها).
    INVALID_STAGE(HttpStatus.CONFLICT),
    // The contact belongs to another company than the one chosen for the deal.
    // جهة الاتصال تابعة لشركة غير الشركة المختارة للصفقة.
    CONTACT_COMPANY_MISMATCH(HttpStatus.CONFLICT),
    // Someone else changed the same record meanwhile (optimistic locking).
    // شخص آخر عدّل نفس السجل في نفس الوقت (Optimistic Locking).
    CONCURRENT_UPDATE(HttpStatus.CONFLICT),
    // An admin cannot disable or demote their own account, so the system is never locked out.
    // منع المدير من إيقاف حسابه أو تغيير صلاحيته بنفسه حتى لا يُقفل النظام.
    CANNOT_MODIFY_SELF(HttpStatus.CONFLICT),
    // Unexpected server error.
    // خطأ غير متوقع في الخادم.
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    // The HTTP status for each code, kept in one place.
    // حالة HTTP المناسبة لكل رمز، محفوظة في مكان واحد.
    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
