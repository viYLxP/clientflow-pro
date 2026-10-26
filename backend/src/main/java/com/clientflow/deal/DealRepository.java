package com.clientflow.deal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealRepository extends JpaRepository<Deal, Long>, JpaSpecificationExecutor<Deal> {

    @Override
    @EntityGraph(attributePaths = {"company", "contact", "owner"})
    Page<Deal> findAll(Specification<Deal> spec, Pageable pageable);

    /** Count and total value per stage. / العدد والقيمة الإجمالية لكل مرحلة. */
    interface StageTotal {
        DealStage getStage();

        long getCount();

        BigDecimal getAmount();
    }

    // ownerId = null means "everyone" (the cast lets PostgreSQL type the null parameter).
    // ownerId = null يعني "الكل" (والـ cast يخلي PostgreSQL يعرف نوع الباراميتر الفاضي).
    @Query("""
            select d.stage as stage, count(d) as count, coalesce(sum(d.amount), 0) as amount
            from Deal d
            where d.stage in :stages and (cast(:ownerId as long) is null or d.owner.id = :ownerId)
            group by d.stage
            """)
    List<StageTotal> totalsByStage(@Param("stages") Collection<DealStage> stages, @Param("ownerId") Long ownerId);

    // The most recently touched deals of one stage, for one board column.
    // أحدث الصفقات تعديلًا في مرحلة وحدة، لعمود واحد في اللوحة.
    @EntityGraph(attributePaths = {"company", "contact", "owner"})
    @Query("""
            select d from Deal d
            where d.stage = :stage and (cast(:ownerId as long) is null or d.owner.id = :ownerId)
            order by d.updatedAt desc, d.id desc
            """)
    List<Deal> boardColumn(@Param("stage") DealStage stage, @Param("ownerId") Long ownerId, Pageable limit);

    long countByCompanyId(Long companyId);

    @Query("select coalesce(sum(d.amount), 0) from Deal d where d.company.id = :companyId and d.stage in :stages")
    BigDecimal sumAmountForCompany(@Param("companyId") Long companyId, @Param("stages") Collection<DealStage> stages);

    @Query("select count(d) from Deal d where d.company.id = :companyId and d.stage in :stages")
    long countForCompany(@Param("companyId") Long companyId, @Param("stages") Collection<DealStage> stages);

    // Closed deals in a period: used for "won this month" and the win rate.
    // الصفقات المغلقة في فترة: تُستخدم لـ "الفوز هذا الشهر" ونسبة الفوز.
    @Query("""
            select count(d) from Deal d
            where d.stage = :stage and d.closedAt >= :since and (cast(:ownerId as long) is null or d.owner.id = :ownerId)
            """)
    long countClosedSince(@Param("stage") DealStage stage, @Param("since") Instant since, @Param("ownerId") Long ownerId);

    @Query("""
            select coalesce(sum(d.amount), 0) from Deal d
            where d.stage = :stage and d.closedAt >= :since and (cast(:ownerId as long) is null or d.owner.id = :ownerId)
            """)
    BigDecimal sumClosedSince(@Param("stage") DealStage stage, @Param("since") Instant since, @Param("ownerId") Long ownerId);

    /** Won value per rep, for the leaderboard. / قيمة الفوز لكل مندوب، لقائمة المتصدرين. */
    interface RepTotal {
        Long getOwnerId();

        String getOwnerName();

        long getCount();

        BigDecimal getAmount();
    }

    @Query("""
            select d.owner.id as ownerId, d.owner.fullName as ownerName, count(d) as count, sum(d.amount) as amount
            from Deal d
            where d.stage = com.clientflow.deal.DealStage.WON and d.closedAt >= :since
            group by d.owner.id, d.owner.fullName
            order by sum(d.amount) desc
            """)
    List<RepTotal> wonByRepSince(@Param("since") Instant since);

    // Open deals expected to close by a date, soonest first ("closing soon" list).
    // الصفقات المفتوحة المتوقع إغلاقها قبل تاريخ، الأقرب أولًا (قائمة "قريبة الإغلاق").
    @EntityGraph(attributePaths = {"company", "owner"})
    @Query("""
            select d from Deal d
            where d.stage in :stages and d.expectedCloseDate is not null and d.expectedCloseDate <= :until
              and (cast(:ownerId as long) is null or d.owner.id = :ownerId)
            order by d.expectedCloseDate asc, d.id asc
            """)
    List<Deal> closingBy(@Param("stages") Collection<DealStage> stages, @Param("until") LocalDate until,
                         @Param("ownerId") Long ownerId, Pageable limit);
}
