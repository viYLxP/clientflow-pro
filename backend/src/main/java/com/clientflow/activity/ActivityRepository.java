package com.clientflow.activity;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ActivityRepository extends JpaRepository<Activity, Long>, JpaSpecificationExecutor<Activity> {

    @Override
    @EntityGraph(attributePaths = {"author", "company", "deal", "contact"})
    Page<Activity> findAll(Specification<Activity> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "company", "deal", "contact"})
    @Query("select a from Activity a order by a.occurredAt desc, a.id desc")
    List<Activity> latest(Pageable limit);
}
