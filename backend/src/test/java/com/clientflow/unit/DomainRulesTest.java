package com.clientflow.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.clientflow.common.ApiException;
import com.clientflow.common.ErrorCode;
import com.clientflow.common.Text;
import com.clientflow.common.Versions;
import com.clientflow.company.Company;
import com.clientflow.deal.Deal;
import com.clientflow.deal.DealStage;
import com.clientflow.task.Task;
import com.clientflow.user.AppUser;
import com.clientflow.user.Role;

/**
 * Pure business rules, tested without Spring or a database.
 * قواعد العمل الصافية، مختبرة بدون Spring ولا قاعدة بيانات.
 */
class DomainRulesTest {

    private final AppUser rep = new AppUser("sara", "Sara", "hash", Role.SALES);

    private Deal deal(String amount) {
        Deal deal = new Deal("Deal", new Company("Co", rep), rep);
        deal.setAmount(new BigDecimal(amount));
        return deal;
    }

    @Test
    void closingAndReopeningKeepClosedAtAndReasonInStep() {
        Deal deal = deal("100");
        Instant now = Instant.parse("2026-01-10T10:00:00Z");
        assertThat(deal.moveTo(DealStage.LOST, "Price", now)).isEqualTo(DealStage.LEAD);
        assertThat(deal.getClosedAt()).isEqualTo(now);
        assertThat(deal.getLostReason()).isEqualTo("Price");
        // LOST → WON: still closed, but the lost reason no longer applies. / من خاسرة لرابحة: الإغلاق باقي والسبب ينمسح.
        deal.moveTo(DealStage.WON, "ignored", now.plusSeconds(60));
        assertThat(deal.getLostReason()).isNull();
        assertThat(deal.getClosedAt()).isEqualTo(now.plusSeconds(60));
        deal.moveTo(DealStage.PROPOSAL, null, now);
        assertThat(deal.getClosedAt()).isNull();
    }

    @Test
    void movingToTheSameStageIsRefused() {
        Deal deal = deal("1");
        assertThatThrownBy(() -> deal.moveTo(DealStage.LEAD, null, Instant.now()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_STAGE);
    }

    @Test
    void weightedAmountRoundsHalfUpToHalalas() {
        // 10% of 0.05 = 0.005 → 0.01 (half-up). / 10% من 0.05 = 0.005 ← 0.01.
        assertThat(deal("0.05").weightedAmount()).isEqualByComparingTo("0.01");
        Deal negotiation = deal("12345.67");
        negotiation.moveTo(DealStage.NEGOTIATION, null, Instant.now());
        assertThat(negotiation.weightedAmount()).isEqualByComparingTo("9259.25");
        Deal lost = deal("500");
        lost.moveTo(DealStage.LOST, "x", Instant.now());
        assertThat(lost.weightedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void taskOverdueOnlyWhileOpenAndBeforeToday() {
        LocalDate today = LocalDate.of(2026, 3, 15);
        Task task = new Task("Call", today.minusDays(1), rep, rep);
        assertThat(task.isOverdue(today)).isTrue();
        assertThat(new Task("Call", today, rep, rep).isOverdue(today)).isFalse();
        task.complete(Instant.now());
        assertThat(task.isOverdue(today)).isFalse();
        Instant first = task.getCompletedAt();
        // Completing twice keeps the original completion time. / الإنجاز مرتين يحتفظ بوقت الإنجاز الأول.
        task.complete(first.plusSeconds(3600));
        assertThat(task.getCompletedAt()).isEqualTo(first);
        task.reopen();
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    void likePatternEscapesWildcardsAndIgnoresBlank() {
        assertThat(Text.likePattern("  ")).isNull();
        assertThat(Text.likePattern(null)).isNull();
        assertThat(Text.likePattern(" 50%_Off ")).isEqualTo("%50\\%\\_off%");
        assertThat(Text.likePattern("a\\b")).isEqualTo("%a\\\\b%");
    }

    @Test
    void versionCheckRequiresTheCurrentVersion() {
        Versions.check(3L, 3L);
        assertThatThrownBy(() -> Versions.check(3L, 2L)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Versions.check(3L, null)).isInstanceOf(ApiException.class);
    }
}
