package com.clientflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.clientflow.config.AppProperties;
import com.clientflow.support.IntegrationTest;

/**
 * Dashboard numbers are computed from live data; each figure is checked against a hand-made scenario.
 * أرقام لوحة التحكم محسوبة من البيانات الحية؛ وكل رقم مفحوص مقابل سيناريو محسوب يدويًا.
 */
class DashboardTest extends IntegrationTest {

    @Autowired
    AppProperties properties;

    private void move(String token, long dealId, String stage, String reason) throws Exception {
        long version = versionOf("deal", dealId);
        String body = reason == null ? "{\"stage\":\"%s\",\"version\":%d}".formatted(stage, version)
                : "{\"stage\":\"%s\",\"lostReason\":\"%s\",\"version\":%d}".formatted(stage, reason, version);
        mvc.perform(auth(post("/api/deals/" + dealId + "/stage"), token).content(body)).andExpect(status().isOk());
    }

    @Test
    void emptyDatabaseShowsZerosAndNoWinRate() throws Exception {
        mvc.perform(auth(get("/api/dashboard"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline.length()").value(4))
                .andExpect(jsonPath("$.openCount").value(0))
                .andExpect(jsonPath("$.weightedForecast").value(0))
                // No closed deals: the win rate is unknown, not 0%. / بدون صفقات مغلقة: النسبة غير معروفة، مو 0%.
                .andExpect(jsonPath("$.winRate").doesNotExist())
                .andExpect(jsonPath("$.myTasks.length()").value(0));
    }

    @Test
    void figuresMatchAHandCalculatedPipeline() throws Exception {
        String sara = saraToken();
        String khalid = khalidToken();
        long c1 = company(sara, "S Co");
        long c2 = company(khalid, "K Co");
        // Open: LEAD 1000 (10%) + PROPOSAL 2000 (50%) for Sara, NEGOTIATION 4000 (75%) for Khalid.
        // مفتوحة: LEAD 1000 و PROPOSAL 2000 لسارة، و NEGOTIATION 4000 لخالد.
        deal(sara, "lead", c1, "1000");
        long proposal = deal(sara, "proposal", c1, "2000");
        move(sara, proposal, "PROPOSAL", null);
        long negotiation = deal(khalid, "negotiation", c2, "4000");
        move(khalid, negotiation, "NEGOTIATION", null);
        // Closed: Sara wins 500 and 300, loses 1; Khalid wins 900.
        // مغلقة: سارة تربح 500 و 300 وتخسر وحدة؛ خالد يربح 900.
        long w1 = deal(sara, "w1", c1, "500");
        move(sara, w1, "WON", null);
        long w2 = deal(sara, "w2", c1, "300");
        move(sara, w2, "WON", null);
        long l1 = deal(sara, "l1", c1, "700");
        move(sara, l1, "LOST", "Price");
        long w3 = deal(khalid, "w3", c2, "900");
        move(khalid, w3, "WON", null);
        // A win from 100 days ago counts in neither "this month" nor the 90-day win rate.
        // فوز قبل 100 يوم ما ينحسب لا في "هذا الشهر" ولا في نسبة الـ 90 يوم.
        long old = deal(sara, "old", c1, "10000");
        move(sara, old, "WON", null);
        jdbc.update("update deal set closed_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(100))), old);

        // Team: 7000 open; weighted 100 + 1000 + 3000 = 4100; won this month 1700; win rate 3 of 4 = 75%.
        // الفريق: 7000 مفتوح؛ مرجّح 4100؛ فوز الشهر 1700؛ نسبة الفوز 3 من 4 = 75%.
        // "This month" in Riyadh: on the 1st of the month only deals closed since midnight count, so the
        // scenario's wins (closed seconds ago) are always inside it.
        // "هذا الشهر" بتوقيت الرياض: الصفقات المغلقة قبل ثواني دائمًا داخله.
        mvc.perform(auth(get("/api/dashboard"), adminToken()))
                .andExpect(jsonPath("$.openCount").value(3))
                .andExpect(jsonPath("$.openAmount").value(7000.00))
                .andExpect(jsonPath("$.weightedForecast").value(4100.00))
                .andExpect(jsonPath("$.wonThisMonthCount").value(3))
                .andExpect(jsonPath("$.wonThisMonthAmount").value(1700.00))
                .andExpect(jsonPath("$.winRate").value(75))
                .andExpect(jsonPath("$.topReps[0].fullName").value("Khalid"))
                .andExpect(jsonPath("$.topReps[0].wonAmount").value(900.00))
                .andExpect(jsonPath("$.topReps[1].wonAmount").value(800.00));

        // Sara only: open 3000, weighted 1100, won 800, win rate 2 of 3 = 67% (66.7 rounded).
        // سارة فقط: مفتوح 3000، مرجّح 1100، فوز 800، نسبة الفوز 2 من 3 = 67%.
        mvc.perform(auth(get("/api/dashboard").param("ownerId", String.valueOf(userId("sara"))), sara))
                .andExpect(jsonPath("$.scopeOwnerId").value(userId("sara")))
                .andExpect(jsonPath("$.openAmount").value(3000.00))
                .andExpect(jsonPath("$.weightedForecast").value(1100.00))
                .andExpect(jsonPath("$.wonThisMonthAmount").value(800.00))
                .andExpect(jsonPath("$.winRate").value(67));
    }

    @Test
    void myTasksAndClosingSoonAreScopedCorrectly() throws Exception {
        String sara = saraToken();
        LocalDate today = LocalDate.now(properties.timeZone());
        long companyId = company(sara, "Tasks Co");
        for (int offset : new int[]{-2, -1, 0, 1}) {
            createId("/api/tasks", """
                    {"title":"t%d","dueDate":"%s","priority":"LOW"}
                    """.formatted(offset, today.plusDays(offset)), sara);
        }
        // Khalid's overdue task must not show on Sara's dashboard. / مهمة خالد المتأخرة ما تظهر في لوحة سارة.
        createId("/api/tasks", "{\"title\":\"k\",\"dueDate\":\"%s\",\"priority\":\"LOW\"}".formatted(today.minusDays(5)), khalidToken());

        createId("/api/deals", """
                {"title":"soon","companyId":%d,"amount":1,"expectedCloseDate":"%s"}
                """.formatted(companyId, today.plusDays(10)), sara);
        createId("/api/deals", """
                {"title":"later","companyId":%d,"amount":1,"expectedCloseDate":"%s"}
                """.formatted(companyId, today.plusDays(60)), sara);

        mvc.perform(auth(get("/api/dashboard"), sara))
                .andExpect(jsonPath("$.myOverdueCount").value(2))
                .andExpect(jsonPath("$.myDueTodayCount").value(1))
                .andExpect(jsonPath("$.myTasks.length()").value(3))
                .andExpect(jsonPath("$.myTasks[0].title").value("t-2"))
                .andExpect(jsonPath("$.closingSoon.length()").value(1))
                .andExpect(jsonPath("$.closingSoon[0].title").value("soon"));
    }
}
