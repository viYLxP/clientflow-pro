package com.clientflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.clientflow.support.IntegrationTest;

/**
 * Companies and contacts: validation, ownership, optimistic locking and delete rules.
 * الشركات وجهات الاتصال: التحقق من المدخلات، والملكية، والقفل المتفائل، وقواعد الحذف.
 */
class CompanyContactApiTest extends IntegrationTest {

    @Test
    void createValidatesAndNormalisesFields() throws Exception {
        String sara = saraToken();
        mvc.perform(auth(post("/api/companies"), sara).content("""
                        {"name":"  ","phone":"abc","email":"not-an-email","website":"no spaces allowed"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.name").value("NotBlank"))
                .andExpect(jsonPath("$.fields.phone").value("Pattern"))
                .andExpect(jsonPath("$.fields.email").value("Email"))
                .andExpect(jsonPath("$.fields.website").value("Pattern"));

        // Blank optional fields become null, the email is lower-cased and the creator becomes the owner.
        // الحقول الاختيارية الفاضية تصير null، والإيميل بأحرف صغيرة، واللي أنشأها يصير مالكها.
        mvc.perform(auth(post("/api/companies"), sara).content("""
                        {"name":" Al Noor ","city":"   ","email":"Info@AlNoor.Example","phone":"+966 11 400 1122"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Al Noor"))
                .andExpect(jsonPath("$.city").doesNotExist())
                .andExpect(jsonPath("$.email").value("info@alnoor.example"))
                .andExpect(jsonPath("$.owner.fullName").value("Sara"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void companyNamesAreUniqueIgnoringCase() throws Exception {
        String sara = saraToken();
        long id = company(sara, "Red Sea Logistics");
        mvc.perform(auth(post("/api/companies"), khalidToken()).content("{\"name\":\"red sea LOGISTICS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NAME_EXISTS"));
        // Renaming a company to its own name (different case) is not a duplicate.
        // إعادة تسمية الشركة لنفس اسمها (بحالة أحرف مختلفة) مو تكرار.
        mvc.perform(auth(put("/api/companies/" + id), sara).content("{\"name\":\"RED SEA LOGISTICS\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void onlyTheOwnerOrAnAdminCanEditAndOnlyAdminsReassign() throws Exception {
        long id = company(saraToken(), "Taiba Schools");
        mvc.perform(auth(put("/api/companies/" + id), khalidToken()).content("{\"name\":\"Hijacked\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
        // A rep cannot give their record away either. / والمندوب ما يقدر يعطي سجله لغيره.
        mvc.perform(auth(put("/api/companies/" + id), saraToken())
                        .content("{\"name\":\"Taiba Schools\",\"ownerId\":%d,\"version\":0}".formatted(userId("khalid"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
        // An admin may reassign the account to another rep. / المدير يقدر ينقل الحساب لمندوب ثاني.
        mvc.perform(auth(put("/api/companies/" + id), adminToken())
                        .content("{\"name\":\"Taiba Schools\",\"ownerId\":%d,\"version\":0}".formatted(userId("khalid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.fullName").value("Khalid"));
        // Reassigning to an inactive user is refused. / النقل لمستخدم معطّل مرفوض.
        jdbc.update("update app_user set active = false where username = 'sara'");
        mvc.perform(auth(put("/api/companies/" + id), adminToken())
                        .content("{\"name\":\"Taiba Schools\",\"ownerId\":%d,\"version\":1}".formatted(userId("sara"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        String sara = saraToken();
        long id = company(sara, "Najd Medical");
        mvc.perform(auth(put("/api/companies/" + id), sara).content("{\"name\":\"Najd Medical Center\",\"version\":0}"))
                .andExpect(status().isOk());
        // A second form still holding version 0 must not overwrite the first edit.
        // نموذج ثاني لسا ماسك النسخة 0 لازم ما يكتب فوق التعديل الأول.
        mvc.perform(auth(put("/api/companies/" + id), sara).content("{\"name\":\"Najd Clinic\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
        // Missing version is refused too, so an old client cannot skip the check.
        // النسخة الناقصة مرفوضة بعد، حتى العميل القديم ما يتخطى الفحص.
        mvc.perform(auth(put("/api/companies/" + id), sara).content("{\"name\":\"Najd Clinic\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void companyWithDealsOrContactsCannotBeDeletedButItsNotesAndTasksGoWithIt() throws Exception {
        String admin = adminToken();
        long busy = company(admin, "Busy Co");
        contact(admin, "Omar", busy);
        deal(admin, "Deal", busy, "1000");
        mvc.perform(auth(delete("/api/companies/" + busy), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"))
                .andExpect(jsonPath("$.deals").value(1))
                .andExpect(jsonPath("$.contacts").value(1));

        long quiet = company(admin, "Quiet Co");
        createId("/api/activities", "{\"type\":\"NOTE\",\"body\":\"hello\",\"companyId\":%d}".formatted(quiet), admin);
        createId("/api/tasks", "{\"title\":\"Call\",\"dueDate\":\"2030-01-01\",\"priority\":\"LOW\",\"companyId\":%d}".formatted(quiet), admin);
        mvc.perform(auth(delete("/api/companies/" + quiet), admin)).andExpect(status().isNoContent());
        assertThat(
                jdbc.queryForObject("select count(*) from activity where company_id = ?", Integer.class, quiet)).isZero();
        assertThat(
                jdbc.queryForObject("select count(*) from task where company_id = ?", Integer.class, quiet)).isZero();
    }

    @Test
    void searchTreatsWildcardsLiterallyAndPagesStably() throws Exception {
        String admin = adminToken();
        company(admin, "100% Fresh");
        company(admin, "1000 Nights");
        // "%" would match both if it were not escaped. / "%" بتطابق الاثنين لو ما انهربت.
        mvc.perform(auth(get("/api/companies").param("q", "100%"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("100% Fresh"));
        mvc.perform(auth(get("/api/companies").param("sort", "nope"), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void companyDetailSumsOpenAndWonDeals() throws Exception {
        String admin = adminToken();
        long id = company(admin, "Qassim Dates");
        deal(admin, "Open 1", id, "1000.50");
        deal(admin, "Open 2", id, "2000");
        long won = deal(admin, "Won", id, "500");
        mvc.perform(auth(post("/api/deals/" + won + "/stage"), admin).content("{\"stage\":\"WON\",\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/companies/" + id), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openDealCount").value(2))
                .andExpect(jsonPath("$.openDealAmount").value(3000.50))
                .andExpect(jsonPath("$.wonAmount").value(500.00));
    }

    @Test
    void contactsFollowTheSameOwnershipRulesAndSearchByFullName() throws Exception {
        String sara = saraToken();
        long companyId = company(sara, "Corniche Group");
        long contactId = createId("/api/contacts", """
                {"firstName":"Yousef","lastName":"Al-Omari","email":"Yousef@Corniche.example","companyId":%d}
                """.formatted(companyId), sara);
        mvc.perform(auth(get("/api/contacts").param("q", "yousef al-om"), sara))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("yousef@corniche.example"))
                .andExpect(jsonPath("$.content[0].company.name").value("Corniche Group"));
        mvc.perform(auth(put("/api/contacts/" + contactId), khalidToken())
                        .content("{\"firstName\":\"X\",\"lastName\":\"Y\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
        mvc.perform(auth(post("/api/contacts"), sara).content("{\"firstName\":\"A\",\"lastName\":\"B\",\"companyId\":999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
