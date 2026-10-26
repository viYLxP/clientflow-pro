package com.clientflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.clientflow.support.IntegrationTest;

/**
 * The deal pipeline: stage rules, history, board totals and record consistency.
 * خط المبيعات: قواعد المراحل، والسجل، وإجماليات اللوحة، وتوافق السجلات.
 */
class DealFlowTest extends IntegrationTest {

    private String stage(String stage, long version) {
        return "{\"stage\":\"%s\",\"version\":%d}".formatted(stage, version);
    }

    @Test
    void dealMovesThroughStagesAndRecordsEveryChange() throws Exception {
        String sara = saraToken();
        long companyId = company(sara, "Al Noor");
        long id = deal(sara, "POS rollout", companyId, "100000");
        mvc.perform(auth(get("/api/deals/" + id), sara))
                .andExpect(jsonPath("$.stage").value("LEAD"))
                .andExpect(jsonPath("$.probability").value(10))
                .andExpect(jsonPath("$.weightedAmount").value(10000.00));

        mvc.perform(auth(post("/api/deals/" + id + "/stage"), sara).content(stage("PROPOSAL", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("PROPOSAL"))
                .andExpect(jsonPath("$.weightedAmount").value(50000.00))
                .andExpect(jsonPath("$.closedAt").doesNotExist());
        mvc.perform(auth(post("/api/deals/" + id + "/stage"), sara).content(stage("WON", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        mvc.perform(auth(get("/api/deals/" + id + "/history"), sara))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].fromStage").doesNotExist())
                .andExpect(jsonPath("$[0].toStage").value("LEAD"))
                .andExpect(jsonPath("$[1].fromStage").value("LEAD"))
                .andExpect(jsonPath("$[2].toStage").value("WON"))
                .andExpect(jsonPath("$[2].changedBy").value("sara"));
    }

    @Test
    void losingNeedsAReasonAndReopeningClearsIt() throws Exception {
        String sara = saraToken();
        long id = deal(sara, "Fleet tracking", company(sara, "Red Sea"), "50000");
        mvc.perform(auth(post("/api/deals/" + id + "/stage"), sara).content("{\"stage\":\"LOST\",\"lostReason\":\"  \",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.lostReason").value("NotBlank"));
        mvc.perform(auth(post("/api/deals/" + id + "/stage"), sara).content("{\"stage\":\"LOST\",\"lostReason\":\"Price\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lostReason").value("Price"));
        // A closed deal is frozen: its terms cannot be edited until it is reopened.
        // الصفقة المغلقة مجمّدة: ما تتعدل بياناتها لين تنفتح من جديد.
        mvc.perform(auth(put("/api/deals/" + id), sara).content("""
                        {"title":"Fleet tracking v2","companyId":%d,"amount":1,"version":1}
                        """.formatted(companyIdOf(id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STAGE"));
        mvc.perform(auth(post("/api/deals/" + id + "/stage"), sara).content(stage("NEGOTIATION", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lostReason").doesNotExist())
                .andExpect(jsonPath("$.closedAt").doesNotExist());
        // Moving to the stage it is already in is refused (nothing would change).
        // النقل لنفس المرحلة الحالية مرفوض (ما بيتغير شي).
        mvc.perform(auth(post("/api/deals/" + id + "/stage"), sara).content(stage("NEGOTIATION", 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STAGE"));
    }

    @Test
    void aDealCannotBeCreatedClosed() throws Exception {
        String sara = saraToken();
        long companyId = company(sara, "Eastern");
        mvc.perform(auth(post("/api/deals"), sara).content("""
                        {"title":"Skip ahead","companyId":%d,"amount":10,"stage":"WON"}
                        """.formatted(companyId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STAGE"));
        // Starting further along the pipeline is allowed and recorded. / البدء من مرحلة متقدمة مسموح ومسجّل.
        long id = createId("/api/deals", """
                {"title":"Warm lead","companyId":%d,"amount":10,"stage":"QUALIFIED"}
                """.formatted(companyId), sara);
        mvc.perform(auth(get("/api/deals/" + id + "/history"), sara))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].toStage").value("QUALIFIED"));
    }

    @Test
    void amountIsValidated() throws Exception {
        String sara = saraToken();
        long companyId = company(sara, "Taiba");
        mvc.perform(auth(post("/api/deals"), sara).content("""
                        {"title":"Bad","companyId":%d,"amount":-1}
                        """.formatted(companyId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").value("DecimalMin"));
        mvc.perform(auth(post("/api/deals"), sara).content("""
                        {"title":"Bad","companyId":%d,"amount":10.555}
                        """.formatted(companyId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").value("Digits"));
    }

    @Test
    void theContactMustWorkAtTheDealsCompany() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        long other = company(sara, "Other Co");
        long faisal = contact(sara, "Faisal", noor);
        long freelancer = contact(sara, "Ahmad", null);
        for (long contactId : new long[]{faisal, freelancer}) {
            mvc.perform(auth(post("/api/deals"), sara).content("""
                            {"title":"Mixed","companyId":%d,"contactId":%d,"amount":5}
                            """.formatted(other, contactId)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONTACT_COMPANY_MISMATCH"));
        }
        mvc.perform(auth(post("/api/deals"), sara).content("""
                        {"title":"Right","companyId":%d,"contactId":%d,"amount":5}
                        """.formatted(noor, faisal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contact.fullName").value("Faisal Test"));
    }

    @Test
    void anotherRepCannotMoveOrEditTheDealButTheAdminCan() throws Exception {
        long id = deal(saraToken(), "Sara's deal", company(saraToken(), "Najd"), "1000");
        mvc.perform(auth(post("/api/deals/" + id + "/stage"), khalidToken()).content(stage("WON", 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
        mvc.perform(auth(post("/api/deals/" + id + "/stage"), adminToken()).content(stage("QUALIFIED", 0)))
                .andExpect(status().isOk());
        // The admin moved it, so the old version 0 held by Sara's open board is now stale.
        // المدير حرّكها، فالنسخة 0 اللي عند لوحة سارة المفتوحة صارت قديمة.
        mvc.perform(auth(post("/api/deals/" + id + "/stage"), saraToken()).content(stage("PROPOSAL", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
        mvc.perform(auth(delete("/api/deals/" + id), saraToken())).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/deals/" + id), adminToken())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from deal_stage_change where deal_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void boardShowsEveryOpenStageWithTrueTotals() throws Exception {
        String sara = saraToken();
        long companyId = company(sara, "Board Co");
        deal(sara, "A", companyId, "100");
        deal(sara, "B", companyId, "250.25");
        long won = deal(sara, "C", companyId, "999");
        mvc.perform(auth(post("/api/deals/" + won + "/stage"), sara).content(stage("WON", 0))).andExpect(status().isOk());
        deal(khalidToken(), "Khalid's", company(khalidToken(), "K Co"), "70");

        mvc.perform(auth(get("/api/deals/board"), sara))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].stage").value("LEAD"))
                .andExpect(jsonPath("$[0].count").value(3))
                .andExpect(jsonPath("$[0].amount").value(420.25))
                // Empty stages still appear, so a card can be moved into them.
                // المراحل الفاضية تظهر بعد، حتى يمكن نقل بطاقة لها.
                .andExpect(jsonPath("$[1].count").value(0))
                .andExpect(jsonPath("$[1].deals.length()").value(0));
        mvc.perform(auth(get("/api/deals/board").param("ownerId", String.valueOf(userId("sara"))), sara))
                .andExpect(jsonPath("$[0].count").value(2));
        mvc.perform(auth(get("/api/deals").param("status", "CLOSED"), sara))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("C"));
    }

    @Test
    void databaseConstraintsBackUpTheStageRules() {
        // Even if a bug bypassed the service, PostgreSQL refuses a LOST deal without a reason.
        // حتى لو فيه خطأ تخطى الخدمة، PostgreSQL يرفض صفقة خاسرة بدون سبب.
        long owner = userId("sara");
        jdbc.update("insert into company (name, owner_id) values ('C', ?)", owner);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        insert into deal (title, company_id, owner_id, stage, amount, closed_at)
                        values ('x', (select id from company), ?, 'LOST', 1, now())
                        """, owner));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        insert into deal (title, company_id, owner_id, stage, amount)
                        values ('x', (select id from company), ?, 'WON', 1)
                        """, owner));
    }

    private long companyIdOf(long dealId) {
        return jdbc.queryForObject("select company_id from deal where id = ?", Long.class, dealId);
    }
}
