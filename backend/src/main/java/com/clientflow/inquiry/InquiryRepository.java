package com.clientflow.inquiry;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for contact-form inquiries.
 * الوصول لجدول استفسارات نموذج التواصل.
 */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    @EntityGraph(attributePaths = {"deal", "handledBy"})
    Page<Inquiry> findByStatus(InquiryStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"deal", "handledBy"})
    Page<Inquiry> findAll(Pageable pageable);

    long countByStatus(InquiryStatus status);

    // Row lock, so two reps handling the same message run one after the other. / قفل صف، حتى مندوبين يعالجون نفس الرسالة يمشون ورا بعض.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inquiry i where i.id = :id")
    Optional<Inquiry> findForUpdate(@Param("id") Long id);
}
