package com.clientflow.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.clientflow.support.IntegrationTest;

/**
 * Login, token handling and role-based access rules.
 * اختبارات تسجيل الدخول، والتعامل مع الرموز، وقواعد الصلاحيات حسب الدور.
 */
class AuthAndSecurityTest extends IntegrationTest {

    @Test
    void loginReturnsTokenAndUserWithoutPasswordHash() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ADMIN\",\"password\":\"%s\"}".formatted(ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                // The hash must never leak into any response.
                // ناتج التشفير لازم ما يطلع في أي رد أبدًا.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameError() throws Exception {
        // Same 401 + code for both cases, so an attacker cannot tell which usernames exist.
        // نفس 401 ونفس الرمز للحالتين، حتى ما يعرف المهاجم أي أسماء مستخدمين موجودة.
        for (String body : new String[]{"{\"username\":\"admin\",\"password\":\"wrong-pass\"}",
                "{\"username\":\"nobody\",\"password\":\"whatever1\"}"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        }
    }

    @Test
    void disabledUserIsRefusedEvenWithAStillValidToken() throws Exception {
        String token = saraToken();
        jdbc.update("update app_user set active = false where username = 'sara'");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sara\",\"password\":\"%s\"}".formatted(SALES_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        // The old token is still correctly signed, but the account is checked on every request.
        // الرمز القديم توقيعه لسا صحيح، لكن الحساب ينفحص مع كل طلب.
        mvc.perform(auth(get("/api/companies"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void roleChangeAppliesWithoutWaitingForTheTokenToExpire() throws Exception {
        String token = adminToken();
        mvc.perform(auth(get("/api/users"), token)).andExpect(status().isOk());
        jdbc.update("update app_user set role = 'SALES' where username = 'admin'");
        mvc.perform(auth(get("/api/users"), token)).andExpect(status().isForbidden());
    }

    @Test
    void requestsWithoutOrWithTamperedTokenAreRejected() throws Exception {
        mvc.perform(get("/api/companies")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        // Changing the signature breaks the HMAC check. / تغيير التوقيع يكسر فحص HMAC.
        String token = saraToken();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        mvc.perform(get("/api/companies").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheLoggedInUser() throws Exception {
        mvc.perform(auth(get("/api/auth/me"), saraToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("sara"))
                .andExpect(jsonPath("$.role").value("SALES"));
    }

    @Test
    void salesRepCannotManageUsersOrDeleteCompanies() throws Exception {
        String sara = saraToken();
        long companyId = company(sara, "Tools Co");
        mvc.perform(auth(get("/api/users"), sara)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(auth(delete("/api/companies/" + companyId), sara)).andExpect(status().isForbidden());
        // The team list (names only) is open to everyone, for owner and assignee pickers.
        // قائمة الفريق (الأسماء فقط) متاحة للكل، لاختيار المالك والمكلّف.
        mvc.perform(auth(get("/api/team"), sara)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].username").doesNotExist());
        mvc.perform(auth(delete("/api/companies/" + companyId), adminToken())).andExpect(status().isNoContent());
    }

    @Test
    void adminManagesUsersButCannotDisableThemself() throws Exception {
        String admin = adminToken();
        long userId = createId("/api/users",
                "{\"username\":\"noura\",\"fullName\":\"Noura\",\"password\":\"Password1\",\"role\":\"SALES\"}", admin);
        // Duplicate username, compared case-insensitively. / اسم مستخدم مكرر، والمقارنة بدون حساسية للأحرف.
        mvc.perform(auth(post("/api/users"), admin)
                        .content("{\"username\":\"NOURA\",\"fullName\":\"Other\",\"password\":\"Password1\",\"role\":\"SALES\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
        login("noura", "Password1");

        mvc.perform(auth(put("/api/users/" + userId("admin")), admin)
                        .content("{\"fullName\":\"Admin\",\"role\":\"ADMIN\",\"active\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
        mvc.perform(auth(put("/api/users/" + userId), admin)
                        .content("{\"fullName\":\"Noura A.\",\"role\":\"SALES\",\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        // An inactive user drops out of the team list, so no new work can be given to them.
        // المستخدم المعطّل يختفي من قائمة الفريق، فما ينعطى شغل جديد.
        mvc.perform(auth(get("/api/team"), admin)).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void weakPasswordIsRejectedWithFieldError() throws Exception {
        mvc.perform(auth(post("/api/users"), adminToken())
                        .content("{\"username\":\"bad name\",\"fullName\":\"X\",\"password\":\"123\",\"role\":\"SALES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.password").value("Size"))
                .andExpect(jsonPath("$.fields.username").value("Pattern"));
    }
}
