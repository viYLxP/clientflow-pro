package com.clientflow.invoice;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;

import org.springframework.stereotype.Component;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;

/**
 * Locks one invoice row for the rest of the transaction.
 * يقفل صف فاتورة واحد لين نهاية المعاملة.
 *
 * <p>An online payment, a bank transfer recorded by staff and voiding can happen at the same moment. Each of them
 * locks the invoice row first, so they run one after the other. The lock also checks the version: if another
 * transaction changed the invoice after we read it, we stop with CONCURRENT_UPDATE instead of acting on old data.</p>
 * <p>الدفع أونلاين، والتحويل البنكي اللي يسجّله الموظف، والإلغاء ممكن يصيرون بنفس اللحظة. كل وحدة منهم تقفل صف
 * الفاتورة أول، فيمشون ورا بعض. والقفل يتحقق كمان من النسخة: لو معاملة ثانية غيّرت الفاتورة بعد ما قريناها، نوقف
 * بـ CONCURRENT_UPDATE بدل ما نشتغل على بيانات قديمة.</p>
 */
@Component
public class InvoiceLock {

    private final EntityManager entityManager;

    public InvoiceLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // Call it before changing the invoice. / ناديها قبل تعديل الفاتورة.
    public void lock(Invoice invoice) {
        try {
            entityManager.lock(invoice, LockModeType.PESSIMISTIC_WRITE);
        } catch (OptimisticLockException e) {
            throw new ApiException(ErrorCode.CONCURRENT_UPDATE);
        }
    }
}
