package com.clientflow.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for invoices.
 * الوصول لجدول الفواتير.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    @Override
    @EntityGraph(attributePaths = {"company", "contact"})
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);

    // The next number from the database sequence, safe when two people create invoices at once.
    // الرقم التالي من sequence قاعدة البيانات، آمن لو شخصين أنشأوا فواتير بنفس الوقت.
    @Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
    long nextNumber();

    @EntityGraph(attributePaths = {"company", "contact", "deal", "createdBy", "lines"})
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findDetail(@Param("id") Long id);

    @EntityGraph(attributePaths = {"company", "contact", "deal", "lines"})
    Optional<Invoice> findByNumberAndCompanyId(String number, Long companyId);

    long countByCompanyId(Long companyId);

    // Money still owed on issued invoices. / المبالغ اللي لسا ما اندفعت على الفواتير الصادرة.
    @Query("""
            select coalesce(sum(i.total), 0) from Invoice i
            where i.status = com.clientflow.invoice.InvoiceStatus.ISSUED
            """)
    BigDecimal outstanding();

    @Query("""
            select count(i) from Invoice i
            where i.status = com.clientflow.invoice.InvoiceStatus.ISSUED and i.dueDate < :today
            """)
    long countOverdue(@Param("today") LocalDate today);
}
