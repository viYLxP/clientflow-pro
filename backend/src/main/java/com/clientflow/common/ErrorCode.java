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
    // The invoice's status does not allow this (e.g. editing an issued invoice, paying a draft).
    // حالة الفاتورة ما تسمح بهالعملية (مثل تعديل فاتورة صادرة، أو دفع مسودة).
    INVALID_STATUS(HttpStatus.CONFLICT),
    // The deal chosen for an invoice belongs to another company.
    // الصفقة المختارة للفاتورة تابعة لشركة ثانية.
    DEAL_COMPANY_MISMATCH(HttpStatus.CONFLICT),
    // Portal access needs a contact with an email (the login) and a company (whose invoices they see).
    // دخول البوابة يحتاج جهة اتصال عندها إيميل (للدخول) وشركة (يشوف فواتيرها).
    PORTAL_NEEDS_EMAIL(HttpStatus.CONFLICT),
    PORTAL_NEEDS_COMPANY(HttpStatus.CONFLICT),
    // The invite link is wrong, already used, or expired. / رابط الدعوة غلط، أو مستخدم، أو منتهي.
    INVITE_INVALID(HttpStatus.BAD_REQUEST),
    // Too many requests from the same address on a public endpoint. / طلبات كثيرة من نفس العنوان على رابط عام.
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    // The invoice cannot be paid now (not issued, already paid, or voided). / الفاتورة ما تنْدفع الحين (مو صادرة، أو مدفوعة، أو ملغاة).
    PAYMENT_NOT_ALLOWED(HttpStatus.CONFLICT),
    // Tabby/Tamara are offered only within their amount limits. / تابي وتمارا متاحين بس داخل حدود المبلغ.
    INSTALMENTS_NOT_AVAILABLE(HttpStatus.CONFLICT),
    // The payment gateway did not answer or refused the request. / بوابة الدفع ما ردّت أو رفضت الطلب.
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY),
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
