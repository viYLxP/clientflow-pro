package com.clientflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.clientflow.support.IntegrationTest;

/**
 * The public contact form and the team's inbox: validation, the spam trap, and converting a message into a lead.
 * نموذج التواصل العام وصندوق الفريق: التحقق، ومصيدة الإزعاج، وتحويل الرسالة لصفقة.
 */
class InquiryTest extends IntegrationTest {

    private static final String MESSAGE = """
            {"name":"Fahad Al-Qahtani","companyName":"Desert Rose Hotels","email":"Fahad@DesertRose.example",
             "phone":"+966 55 300 4411","message":"We run 6 hotels and want a demo."}""";

    private static MockHttpServletRequestBuilder send(String body) {
        return post("/api/public/inquiries").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void visitorsSendWithoutLoggingInAndTheTeamSeesIt() throws Exception {
        mvc.perform(send("{\"name\":\"\",\"email\":\"nope\",\"message\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("NotBlank"))
                .andExpect(jsonPath("$.fields.email").value("Email"))
                .andExpect(jsonPath("$.fields.message").value("Size"));
        mvc.perform(send(MESSAGE)).andExpect(status().isAccepted());
        // The hidden honeypot field is filled only by bots: accepted, but nothing is saved.
        // حقل المصيدة المخفي يعبّيه الروبوت بس: ينقبل، بس ما ينحفظ شي.
        mvc.perform(send(MESSAGE.replace("}", ",\"website\":\"http://spam.example\"}"))).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("select count(*) from inquiry", Integer.class)).isEqualTo(1);

        String sara = saraToken();
        mvc.perform(auth(get("/api/inquiries?status=NEW"), sara))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("fahad@desertrose.example"))
                .andExpect(jsonPath("$.content[0].status").value("NEW"));
        mvc.perform(auth(get("/api/dashboard"), sara)).andExpect(jsonPath("$.billing.newInquiries").value(1));
        // The inbox is for staff only. / الصندوق للموظفين بس.
        mvc.perform(get("/api/inquiries")).andExpect(status().isUnauthorized());
    }

    @Test
    void convertingCreatesTheCompanyContactLeadAndNoteOnce() throws Exception {
        mvc.perform(send(MESSAGE)).andExpect(status().isAccepted());
        long id = jdbc.queryForObject("select id from inquiry", Long.class);
        String sara = saraToken();
        mvc.perform(auth(post("/api/inquiries/%d/convert".formatted(id)), sara)
                        .content("{\"dealTitle\":\"Corporate booking CRM\",\"amount\":60000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inquiry.status").value("CONVERTED"))
                .andExpect(jsonPath("$.inquiry.deal.title").value("Corporate booking CRM"))
                .andExpect(jsonPath("$.inquiry.handledBy.fullName").value("Sara"))
                .andExpect(jsonPath("$.contact.fullName").value("Fahad Al-Qahtani"));

        Map<String, Object> deal = jdbc.queryForMap("""
                select d.stage, d.amount, c.name as company, u.username as owner, ct.email, ct.phone, ct.last_name
                from deal d join company c on c.id = d.company_id join app_user u on u.id = d.owner_id
                join contact ct on ct.id = d.contact_id""");
        assertThat(deal).containsEntry("stage", "LEAD").containsEntry("company", "Desert Rose Hotels")
                .containsEntry("owner", "sara").containsEntry("email", "fahad@desertrose.example")
                .containsEntry("phone", "+966 55 300 4411").containsEntry("last_name", "Al-Qahtani");
        assertThat(deal.get("amount").toString()).isEqualTo("60000.00");
        // The message is on the deal's timeline. / الرسالة في سجل الصفقة.
        assertThat(jdbc.queryForObject("select body from activity", String.class))
                .contains("We run 6 hotels and want a demo.");

        // Handled once only. / تنعالج مرة وحدة بس.
        mvc.perform(auth(post("/api/inquiries/%d/convert".formatted(id)), khalidToken()).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
        mvc.perform(auth(post("/api/inquiries/%d/dismiss".formatted(id)), sara))
                .andExpect(status().isConflict());

        // A second message from the same person reuses the company and the contact. / رسالة ثانية من نفس الشخص تعيد استخدام الشركة وجهة الاتصال.
        mvc.perform(send(MESSAGE.replace("want a demo", "need a price"))).andExpect(status().isAccepted());
        long second = jdbc.queryForObject("select max(id) from inquiry", Long.class);
        mvc.perform(auth(post("/api/inquiries/%d/convert".formatted(second)), khalidToken()).content("{}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from company", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from contact", Integer.class)).isEqualTo(1);
        // With no title the lead is named after the company, and the rep converting it owns it.
        // بدون عنوان الصفقة تتسمى باسم الشركة، والمندوب اللي حوّلها هو مالكها.
        assertThat(jdbc.queryForMap("select d.title, d.amount, u.username from deal d join app_user u on u.id = d.owner_id"
                + " order by d.id desc limit 1"))
                .containsEntry("title", "Desert Rose Hotels").containsEntry("username", "khalid");
    }

    @Test
    void aVisitorWithoutACompanyAndDismissing() throws Exception {
        mvc.perform(send("""
                {"name":"Sami","email":"sami@example.com","message":"Is there a plan for one person?"}"""))
                .andExpect(status().isAccepted());
        mvc.perform(send("""
                {"name":"SEO Booster","email":"offers@seo.example","message":"Rank #1 on Google in 7 days!!!"}"""))
                .andExpect(status().isAccepted());
        long sami = jdbc.queryForObject("select id from inquiry where name = 'Sami'", Long.class);
        long spam = jdbc.queryForObject("select id from inquiry where name = 'SEO Booster'", Long.class);
        String khalid = khalidToken();
        mvc.perform(auth(post("/api/inquiries/%d/convert".formatted(sami)), khalid).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.fullName").value("Sami"));
        assertThat(jdbc.queryForObject("select name from company", String.class)).isEqualTo("Sami");
        // A rep cannot hand the lead to someone else; only an admin assigns. / المندوب ما يعطي الصفقة لغيره؛ التعيين للمدير.
        mvc.perform(auth(post("/api/inquiries/%d/convert".formatted(spam)), khalid)
                        .content("{\"ownerId\":%d}".formatted(userId("sara"))))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/inquiries/%d/dismiss".formatted(spam)), khalid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.handledAt").exists());
        mvc.perform(auth(get("/api/inquiries?status=DISMISSED"), khalid))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void anyStaffMemberCanReadInquiries() throws Exception {
        mvc.perform(send(MESSAGE)).andExpect(status().isAccepted());
        long id = jdbc.queryForObject("select id from inquiry", Long.class);
        mvc.perform(auth(get("/api/inquiries/" + id), khalidToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Desert Rose Hotels"));
        mvc.perform(auth(get("/api/inquiries/999"), khalidToken())).andExpect(status().isNotFound());
    }
}
