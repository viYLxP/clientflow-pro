package com.clientflow.dashboard;

import java.math.BigDecimal;
import java.util.List;

import com.clientflow.activity.ActivityDtos.ActivityResponse;
import com.clientflow.deal.DealDtos.DealResponse;
import com.clientflow.deal.DealStage;
import com.clientflow.task.TaskDtos.TaskResponse;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record StageSummary(DealStage stage, int probability, long count, BigDecimal amount,
                               BigDecimal weightedAmount) {
    }

    public record RepSummary(Long userId, String fullName, long wonCount, BigDecimal wonAmount) {
    }

    /**
     * @param scopeOwnerId       null when the numbers cover the whole team / null لو الأرقام للفريق كامل
     * @param winRate            won ÷ (won + lost) over the last 90 days, 0–100; null when nothing closed
     *                           / نسبة الفوز آخر 90 يوم من 0 إلى 100؛ null لو ما انغلق شي
     * @param myTasks            the caller's open tasks due today or earlier (always personal)
     *                           / مهام المستخدم المفتوحة المستحقة اليوم أو قبل (شخصية دائمًا)
     */
    public record DashboardResponse(Long scopeOwnerId,
                                    List<StageSummary> pipeline,
                                    long openCount,
                                    BigDecimal openAmount,
                                    BigDecimal weightedForecast,
                                    long wonThisMonthCount,
                                    BigDecimal wonThisMonthAmount,
                                    Integer winRate,
                                    long myOverdueCount,
                                    long myDueTodayCount,
                                    List<TaskResponse> myTasks,
                                    List<RepSummary> topReps,
                                    List<DealResponse> closingSoon,
                                    List<ActivityResponse> recentActivities) {
    }
}
