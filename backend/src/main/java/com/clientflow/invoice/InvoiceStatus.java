package com.clientflow.invoice;

/**
 * Invoice life cycle: DRAFT (editable, the client cannot see it) → ISSUED (sent, can be paid) → PAID.
 * An issued or paid invoice can be VOIDed with a reason; a paid one is refunded first.
 * دورة حياة الفاتورة: DRAFT (تنعدّل، والعميل ما يشوفها) ← ISSUED (انرسلت، وتنْدفع) ← PAID.
 * الفاتورة الصادرة أو المدفوعة تنلغي (VOID) مع سبب؛ والمدفوعة تنسترجع فلوسها أول.
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    VOID;

    // What the client portal shows: everything except drafts. / اللي تعرضه بوابة العميل: كل شي إلا المسودات.
    public boolean visibleToClient() {
        return this != DRAFT;
    }
}
