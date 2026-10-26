package com.clientflow.task;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @Override
    @EntityGraph(attributePaths = {"assignee", "createdBy", "company", "deal", "contact"})
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    // A user's open tasks due on or before a date (overdue + today on the dashboard), most urgent first.
    // مهام المستخدم المفتوحة المستحقة لحد تاريخ معيّن (المتأخرة + اليوم في اللوحة)، الأكثر إلحاحًا أولًا.
    @EntityGraph(attributePaths = {"company", "deal"})
    @Query("""
            select t from Task t
            where t.assignee.id = :assigneeId and t.status = com.clientflow.task.TaskStatus.OPEN and t.dueDate <= :until
            order by t.dueDate asc, t.id asc
            """)
    List<Task> openDueBy(@Param("assigneeId") Long assigneeId, @Param("until") LocalDate until, Pageable limit);

    @Query("""
            select count(t) from Task t
            where t.assignee.id = :assigneeId and t.status = com.clientflow.task.TaskStatus.OPEN and t.dueDate < :today
            """)
    long countOverdue(@Param("assigneeId") Long assigneeId, @Param("today") LocalDate today);

    @Query("""
            select count(t) from Task t
            where t.assignee.id = :assigneeId and t.status = com.clientflow.task.TaskStatus.OPEN and t.dueDate = :today
            """)
    long countDueOn(@Param("assigneeId") Long assigneeId, @Param("today") LocalDate today);

}
