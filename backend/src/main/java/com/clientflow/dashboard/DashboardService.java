package com.clientflow.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clientflow.activity.ActivityDtos.ActivityResponse;
import com.clientflow.activity.ActivityRepository;
import com.clientflow.config.AppProperties;
import com.clientflow.dashboard.DashboardDtos.DashboardResponse;
import com.clientflow.dashboard.DashboardDtos.RepSummary;
import com.clientflow.dashboard.DashboardDtos.StageSummary;
import com.clientflow.deal.DealDtos.DealResponse;
import com.clientflow.deal.DealRepository;
import com.clientflow.deal.DealRepository.StageTotal;
import com.clientflow.deal.DealService;
import com.clientflow.deal.DealStage;
import com.clientflow.task.TaskDtos.TaskResponse;
import com.clientflow.task.TaskRepository;
import com.clientflow.user.AppUser;
import com.clientflow.user.CurrentUserService;

/**
 * Builds the dashboard from live data (no cached or hard-coded figures).
 * يبني لوحة التحكم من البيانات الحية (بدون أرقام مخزّنة أو ثابتة).
 */
@Service
public class DashboardService {

    static final int WIN_RATE_DAYS = 90;
    static final int CLOSING_SOON_DAYS = 30;
    static final int LIST_SIZE = 6;

    private final DealRepository deals;
    private final TaskRepository tasks;
    private final ActivityRepository activities;
    private final CurrentUserService currentUser;
    private final AppProperties properties;

    public DashboardService(DealRepository deals, TaskRepository tasks, ActivityRepository activities,
                            CurrentUserService currentUser, AppProperties properties) {
        this.deals = deals;
        this.tasks = tasks;
        this.activities = activities;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    /**
     * @param ownerId limit the pipeline and sales numbers to one rep; null = whole team
     *                / حصر أرقام خط المبيعات والمبيعات على مندوب واحد؛ null = الفريق كامل
     */
    @Transactional(readOnly = true)
    public DashboardResponse build(Long ownerId) {
        AppUser me = currentUser.require();
        ZoneId zone = properties.timeZone();
        LocalDate today = LocalDate.now(zone);
        // Period starts in the business time zone, converted to instants for the database.
        // بدايات الفترات بتوقيت النشاط، محوّلة لـ Instant لقاعدة البيانات.
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant winRateStart = today.minusDays(WIN_RATE_DAYS - 1L).atStartOfDay(zone).toInstant();

        Map<DealStage, StageTotal> totals = deals.totalsByStage(DealService.OPEN, ownerId).stream()
                .collect(Collectors.toMap(StageTotal::getStage, Function.identity()));
        List<StageSummary> pipeline = Arrays.stream(DealStage.values())
                .filter(DealService.OPEN::contains)
                .map(stage -> summary(stage, totals.get(stage)))
                .toList();

        long won = deals.countClosedSince(DealStage.WON, winRateStart, ownerId);
        long lost = deals.countClosedSince(DealStage.LOST, winRateStart, ownerId);
        PageRequest top = PageRequest.of(0, LIST_SIZE);

        return new DashboardResponse(ownerId,
                pipeline,
                pipeline.stream().mapToLong(StageSummary::count).sum(),
                pipeline.stream().map(StageSummary::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                pipeline.stream().map(StageSummary::weightedAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                deals.countClosedSince(DealStage.WON, monthStart, ownerId),
                deals.sumClosedSince(DealStage.WON, monthStart, ownerId),
                winRate(won, lost),
                tasks.countOverdue(me.getId(), today),
                tasks.countDueOn(me.getId(), today),
                tasks.openDueBy(me.getId(), today, top).stream().map(t -> TaskResponse.from(t, today)).toList(),
                deals.wonByRepSince(monthStart).stream().limit(5)
                        .map(r -> new RepSummary(r.getOwnerId(), r.getOwnerName(), r.getCount(), r.getAmount()))
                        .toList(),
                deals.closingBy(DealService.OPEN, today.plusDays(CLOSING_SOON_DAYS), ownerId, top).stream()
                        .map(DealResponse::from).toList(),
                activities.latest(top).stream().map(ActivityResponse::from).toList());
    }

    static StageSummary summary(DealStage stage, StageTotal total) {
        long count = total == null ? 0 : total.getCount();
        BigDecimal amount = total == null ? BigDecimal.ZERO : total.getAmount();
        // Weighted per stage total, so the sum equals Σ(amount × probability) without per-deal rounding drift.
        // الترجيح على إجمالي المرحلة، فالمجموع يساوي Σ(القيمة × الاحتمال) بدون فروقات تقريب لكل صفقة.
        BigDecimal weighted = amount.multiply(BigDecimal.valueOf(stage.probability()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new StageSummary(stage, stage.probability(), count, amount, weighted);
    }

    /**
     * Whole-number percentage, rounded half-up; null (shown as "—") when nothing closed, instead of a misleading 0%.
     * نسبة مئوية بدون كسور ومقرّبة لأعلى عند النصف؛ null (تظهر "—") لو ما انغلق شي، بدل 0% المضللة.
     */
    static Integer winRate(long won, long lost) {
        long closed = won + lost;
        if (closed == 0) {
            return null;
        }
        return (int) ((won * 200 + closed) / (closed * 2));
    }
}
