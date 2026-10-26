package com.clientflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.clientflow.config.AppProperties;
import com.clientflow.support.IntegrationTest;

/**
 * Follow-up tasks (due windows, completion, edit rights) and the activity log.
 * مهام المتابعة (نطاقات الاستحقاق، الإنجاز، صلاحيات التعديل) وسجل التواصل.
 */
class TaskActivityTest extends IntegrationTest {

    @Autowired
    AppProperties properties;

    private LocalDate today() {
        return LocalDate.now(properties.timeZone());
    }

    private long task(String token, String title, LocalDate due, String extra) throws Exception {
        return createId("/api/tasks", """
                {"title":"%s","dueDate":"%s","priority":"MEDIUM"%s}
                """.formatted(title, due, extra), token);
    }

    @Test
    void dueFiltersUseTheBusinessCalendarAndIgnoreDoneTasks() throws Exception {
        String sara = saraToken();
        LocalDate today = today();
        task(sara, "Late", today.minusDays(1), "");
        task(sara, "Today", today, "");
        task(sara, "In six days", today.plusDays(6), "");
        task(sara, "In a week", today.plusDays(7), "");
        long done = task(sara, "Late but done", today.minusDays(3), "");
        mvc.perform(auth(post("/api/tasks/" + done + "/complete"), sara).content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.overdue").value(false))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mvc.perform(auth(get("/api/tasks").param("due", "OVERDUE"), sara))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Late"))
                .andExpect(jsonPath("$.content[0].overdue").value(true));
        mvc.perform(auth(get("/api/tasks").param("due", "TODAY"), sara))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].overdue").value(false));
        // Today plus the next six days. / اليوم والستة أيام اللي بعده.
        mvc.perform(auth(get("/api/tasks").param("due", "WEEK"), sara))
                .andExpect(jsonPath("$.totalElements").value(2));
        // Default order is soonest due first. / الترتيب الافتراضي الأقرب استحقاقًا أولًا.
        mvc.perform(auth(get("/api/tasks").param("status", "OPEN"), sara))
                .andExpect(jsonPath("$.content[0].title").value("Late"))
                .andExpect(jsonPath("$.content[3].title").value("In a week"));
    }

    @Test
    void completeAndReopenKeepCompletedAtInStep() throws Exception {
        String sara = saraToken();
        long id = task(sara, "Send proposal", today(), "");
        mvc.perform(auth(post("/api/tasks/" + id + "/complete"), sara).content("{\"version\":0}")).andExpect(status().isOk());
        mvc.perform(auth(post("/api/tasks/" + id + "/reopen"), sara).content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
        mvc.perform(auth(post("/api/tasks/" + id + "/complete"), sara).content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
        mvc.perform(auth(post("/api/tasks/" + id + "/complete"), sara).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.version").value("NotNull"));
    }

    @Test
    void assigneeCreatorAndAdminMayChangeATaskOthersMayNot() throws Exception {
        String khalid = khalidToken();
        // Khalid hands a task to Sara. / خالد يكلّف سارة بمهمة.
        long id = task(khalid, "Intro call", today(), ",\"assigneeId\":%d".formatted(userId("sara")));
        mvc.perform(auth(get("/api/tasks/" + id), khalid))
                .andExpect(jsonPath("$.assignee.fullName").value("Sara"))
                .andExpect(jsonPath("$.createdBy.fullName").value("Khalid"));
        mvc.perform(auth(post("/api/tasks/" + id + "/complete"), saraToken()).content("{\"version\":0}"))
                .andExpect(status().isOk());

        long saraOnly = task(saraToken(), "Private follow-up", today(), "");
        mvc.perform(auth(post("/api/tasks/" + saraOnly + "/complete"), khalid).content("{\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
        mvc.perform(auth(delete("/api/tasks/" + saraOnly), khalid)).andExpect(status().isForbidden());
        // Unlike companies or deals, a rep may delete their own task. / بعكس الشركات والصفقات، المندوب يحذف مهمته.
        mvc.perform(auth(delete("/api/tasks/" + saraOnly), saraToken())).andExpect(status().isNoContent());
    }

    @Test
    void tasksCannotGoToInactiveUsers() throws Exception {
        jdbc.update("update app_user set active = false where username = 'khalid'");
        mvc.perform(auth(post("/api/tasks"), saraToken()).content("""
                        {"title":"x","dueDate":"%s","priority":"LOW","assigneeId":%d}
                        """.formatted(today(), userId("khalid"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void linksAreDerivedAndMustAgree() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        long other = company(sara, "Other");
        long dealId = deal(sara, "POS", noor, "10");
        long otherContact = contact(sara, "Omar", other);

        // A task on a deal also belongs to the deal's company. / المهمة على صفقة تتبع شركة الصفقة بعد.
        long taskId = task(sara, "On the deal", today(), ",\"dealId\":%d".formatted(dealId));
        mvc.perform(auth(get("/api/tasks/" + taskId), sara))
                .andExpect(jsonPath("$.company.name").value("Al Noor"))
                .andExpect(jsonPath("$.deal.title").value("POS"));
        mvc.perform(auth(post("/api/tasks"), sara).content("""
                        {"title":"x","dueDate":"%s","priority":"LOW","dealId":%d,"companyId":%d}
                        """.formatted(today(), dealId, other)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.companyId").value("Mismatch"));
        mvc.perform(auth(post("/api/activities"), sara).content("""
                        {"type":"CALL","body":"Wrong person","dealId":%d,"contactId":%d}
                        """.formatted(dealId, otherContact)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTACT_COMPANY_MISMATCH"));
        // With only a contact, the activity lands on that contact's company timeline.
        // لو فيه جهة اتصال بس، النشاط ينزل في سجل شركتها.
        mvc.perform(auth(post("/api/activities"), sara).content("""
                        {"type":"CALL","body":"Called Omar","contactId":%d}
                        """.formatted(otherContact)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company.name").value("Other"));
    }

    @Test
    void activitiesNeedACompanyAndCannotBeInTheFuture() throws Exception {
        String sara = saraToken();
        long freelancer = contact(sara, "Ahmad", null);
        mvc.perform(auth(post("/api/activities"), sara).content("""
                        {"type":"NOTE","body":"No company","contactId":%d}
                        """.formatted(freelancer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.companyId").value("NotNull"));
        long companyId = company(sara, "Najd");
        mvc.perform(auth(post("/api/activities"), sara).content("""
                        {"type":"MEETING","body":"Tomorrow?","occurredAt":"%s","companyId":%d}
                        """.formatted(Instant.now().plus(Duration.ofHours(2)), companyId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.occurredAt").value("PastOrPresent"));
        mvc.perform(auth(post("/api/activities"), sara).content("{\"type\":\"NOTE\",\"body\":\"   \",\"companyId\":%d}".formatted(companyId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.body").value("NotBlank"));
    }

    @Test
    void timelineIsNewestFirstAndOnlyTheAuthorOrAdminDeletes() throws Exception {
        String sara = saraToken();
        long companyId = company(sara, "Corniche");
        Instant now = Instant.now();
        createId("/api/activities", """
                {"type":"EMAIL","body":"older","occurredAt":"%s","companyId":%d}
                """.formatted(now.minus(Duration.ofDays(2)), companyId), sara);
        long newer = createId("/api/activities", """
                {"type":"CALL","body":"newer","occurredAt":"%s","companyId":%d}
                """.formatted(now.minus(Duration.ofHours(1)), companyId), sara);
        mvc.perform(auth(get("/api/activities").param("companyId", String.valueOf(companyId)), sara))
                .andExpect(jsonPath("$.content[0].body").value("newer"))
                .andExpect(jsonPath("$.content[0].author.fullName").value("Sara"))
                .andExpect(jsonPath("$.content[1].body").value("older"));
        mvc.perform(auth(delete("/api/activities/" + newer), khalidToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
        mvc.perform(auth(delete("/api/activities/" + newer), adminToken())).andExpect(status().isNoContent());
        // There is no edit endpoint: the log is append-only. / ما فيه تعديل: السجل للإضافة فقط.
        mvc.perform(auth(put("/api/activities/" + newer), sara).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
