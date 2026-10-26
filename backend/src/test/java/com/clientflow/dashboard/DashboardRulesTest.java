package com.clientflow.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.clientflow.deal.DealStage;

/**
 * Rounding rules of the dashboard figures (same package, so the helpers can stay package-private).
 * قواعد تقريب أرقام اللوحة (نفس الحزمة، حتى الدوال المساعدة تبقى داخلية).
 */
class DashboardRulesTest {

    @Test
    void winRateRoundsHalfUpAndIsUnknownWithoutClosedDeals() {
        assertThat(DashboardService.winRate(0, 0)).isNull();
        assertThat(DashboardService.winRate(0, 5)).isZero();
        assertThat(DashboardService.winRate(2, 1)).isEqualTo(67);
        assertThat(DashboardService.winRate(1, 2)).isEqualTo(33);
        // Exactly half a percent rounds up: 1 of 200 = 0.5% → 1%. / نص بالمية بالضبط يتقرب لفوق.
        assertThat(DashboardService.winRate(1, 199)).isEqualTo(1);
        assertThat(DashboardService.winRate(7, 0)).isEqualTo(100);
    }

    @Test
    void emptyStageSummaryIsZeroNotNull() {
        var summary = DashboardService.summary(DealStage.PROPOSAL, null);
        assertThat(summary.count()).isZero();
        assertThat(summary.weightedAmount()).isEqualByComparingTo("0");
        assertThat(summary.probability()).isEqualTo(50);
    }
}
