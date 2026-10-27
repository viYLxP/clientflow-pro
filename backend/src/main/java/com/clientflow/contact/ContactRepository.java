package com.clientflow.contact;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContactRepository extends JpaRepository<Contact, Long>, JpaSpecificationExecutor<Contact> {

    // Company and owner come in the same query (no N+1 on list pages).
    // الشركة والمالك يجون بنفس الاستعلام (بدون N+1 في صفحات القوائم).
    @Override
    @EntityGraph(attributePaths = {"company", "owner"})
    Page<Contact> findAll(Specification<Contact> spec, Pageable pageable);

    long countByCompanyId(Long companyId);

    // Finds the person again when a known client writes through the contact form. / يلقى الشخص لو عميل معروف كتب من نموذج التواصل.
    Optional<Contact> findFirstByCompanyIdAndEmailIgnoreCase(Long companyId, String email);
}
