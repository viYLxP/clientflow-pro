package com.clientflow.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.clientflow.invoice.Invoice;

/**
 * Database access for payments.
 * الوصول لجدول المدفوعات.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoiceOrderByCreatedAtAsc(Invoice invoice);

    Optional<Payment> findFirstByInvoiceAndStatus(Invoice invoice, Payment.Status status);

    List<Payment> findByInvoiceAndStatus(Invoice invoice, Payment.Status status);

    // The payment page and the gateways need the invoice, its lines and its company in one go.
    // صفحة الدفع والبوابات تحتاج الفاتورة وأسطرها وشركتها مرة وحدة.
    @EntityGraph(attributePaths = {"invoice", "invoice.lines", "invoice.company", "invoice.contact"})
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findWithInvoice(@Param("id") UUID id);

    // Money actually received in a period (refunds excluded), for the dashboard.
    // الفلوس اللي وصلت فعلًا في فترة (بدون المسترجع)، للوحة التحكم.
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = com.clientflow.payment.Payment.Status.PAID and p.paidAt >= :from and p.paidAt < :to
            """)
    BigDecimal collected(@Param("from") Instant from, @Param("to") Instant to);
}
