package com.clientflow.company;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CompanyRepository extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {

    Optional<Company> findByNameIgnoreCase(String name);

    // Loads the owner in the same query, so a list page does not run one extra query per row (the "N+1" problem).
    // يحمّل المالك في نفس الاستعلام، حتى صفحة القائمة ما تسوي استعلام إضافي لكل صف (مشكلة "N+1").
    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Company> findAll(Specification<Company> spec, Pageable pageable);
}
