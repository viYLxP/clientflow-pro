package com.clientflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.clientflow.support.IntegrationTest;

/**
 * The client portal: invites, what a client may see, paying an invoice, and keeping clients out of the CRM.
 * بوابة العملاء: الدعوات، واللي يقدر العميل يشوفه، ودفع الفاتورة، وإبعاد العملاء عن النظام الداخلي.
 */
class PortalTest extends IntegrationTest {

    @Test
    void inviteNeedsAnEmailAndACompanyAndTheLinkWorksOnce() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        long noEmail = contact(sara, "Nada", noor);
        long freelancer = contactWithEmail(sara, "Ahmad", null, "ahmad@example.com");
        mvc.perform(auth(post("/api/contacts/%d/portal-access/invite".formatted(noEmail)), sara))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTAL_NEEDS_EMAIL"));
        mvc.perform(auth(post("/api/contacts/%d/portal-access/invite".formatted(freelancer)), sara))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTAL_NEEDS_COMPANY"));
        // Only the contact's owner or an admin manages access. / بس مالك جهة الاتصال أو المدير يدير الدخول.
        long reem = contactWithEmail(sara, "Reem", noor, "Reem@AlNoor.example");
        mvc.perform(auth(post("/api/contacts/%d/portal-access/invite".formatted(reem)), khalidToken()))
                .andExpect(status().isForbidden());

        mvc.perform(auth(get("/api/contacts/%d/portal-access".formatted(reem)), sara))
                .andExpect(jsonPath("$.status").value("NONE"));
        String token = inviteToken(sara, reem);
        mvc.perform(auth(get("/api/contacts/%d/portal-access".formatted(reem)), sara))
                .andExpect(jsonPath("$.status").value("INVITED"))
                .andExpect(jsonPath("$.login").value("reem@alnoor.example"))
                .andExpect(jsonPath("$.inviteExpiresAt").exists());
        // Only the hash is stored. / نخزّن الـ hash بس.
        assertThat(jdbc.queryForObject("select invite_token_hash from app_user where username = 'reem@alnoor.example'",
                String.class)).hasSize(64).isNotEqualTo(token);
        // Not signed in before the client chooses a password. / ما يدخل قبل ما يختار العميل كلمة المرور.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reem@alnoor.example\",\"password\":\"anything1\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/invite/check").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Reem Client"))
                .andExpect(jsonPath("$.companyName").value("Al Noor"));
        mvc.perform(post("/api/auth/invite/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"short\"}".formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").value("Size"));
        mvc.perform(post("/api/auth/invite/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, CLIENT_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.role").value("CLIENT"));
        // Used once; a made-up token gets the same answer. / مستخدم مرة؛ والرمز المختلق نفس الرد.
        mvc.perform(post("/api/auth/invite/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, CLIENT_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));
        mvc.perform(post("/api/auth/invite/check").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"made-up\"}"))
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));
        login("reem@alnoor.example", CLIENT_PASSWORD);
        mvc.perform(auth(get("/api/contacts/%d/portal-access".formatted(reem)), sara))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void anExpiredInviteAndADisabledLoginAreRefused() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        long reem = contactWithEmail(sara, "Reem", noor, "reem@alnoor.example");
        String expired = inviteToken(sara, reem);
        jdbc.update("update app_user set invite_expires_at = now() - interval '1 minute' where contact_id = ?", reem);
        mvc.perform(post("/api/auth/invite/check").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(expired)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_INVALID"));

        // A new link replaces the old one. / الرابط الجديد يحل محل القديم.
        String client = clientToken(sara, reem);
        mvc.perform(auth(get("/api/portal/me"), client)).andExpect(status().isOk());
        mvc.perform(auth(post("/api/contacts/%d/portal-access/disable".formatted(reem)), sara))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        // The token they already hold stops working at once. / الرمز اللي معه يوقف فورًا.
        mvc.perform(auth(get("/api/portal/me"), client)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"reem@alnoor.example\",\"password\":\"%s\"}".formatted(CLIENT_PASSWORD)))
                .andExpect(status().isUnauthorized());

        // Deleting the contact deletes the login with it. / حذف جهة الاتصال يحذف الدخول معها.
        mvc.perform(auth(delete("/api/contacts/" + reem), adminToken())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from app_user where role = 'CLIENT'", Integer.class)).isZero();
    }

    @Test
    void clientsStayOutOfTheCrmAndStaffOutOfThePortal() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        String client = clientToken(sara, contactWithEmail(sara, "Reem", noor, "reem@alnoor.example"));
        mvc.perform(auth(get("/api/auth/me"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLIENT"));
        for (String url : new String[]{"/api/companies", "/api/deals", "/api/dashboard", "/api/team", "/api/invoices",
                "/api/inquiries", "/api/contacts/1/portal-access"}) {
            mvc.perform(auth(get(url), client)).andExpect(status().isForbidden());
        }
        mvc.perform(auth(get("/api/portal/invoices"), sara)).andExpect(status().isForbidden());
        // Client logins are not team members and cannot be managed as users. / دخول العملاء مو من الفريق وما ينْدار كمستخدم.
        mvc.perform(auth(get("/api/team"), sara)).andExpect(jsonPath("$.length()").value(3));
        String admin = adminToken();
        mvc.perform(auth(get("/api/users"), admin)).andExpect(jsonPath("$.length()").value(3));
        long clientId = userId("reem@alnoor.example");
        mvc.perform(auth(put("/api/users/" + clientId), admin)
                        .content("{\"fullName\":\"X\",\"role\":\"ADMIN\",\"active\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/users"), admin)
                        .content("{\"username\":\"newclient\",\"fullName\":\"X\",\"password\":\"Passw0rd!\",\"role\":\"CLIENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.role").value("Staff"));
        // A client cannot be made the owner of a record. / العميل ما ينحط مالك لسجل.
        mvc.perform(auth(post("/api/companies"), admin)
                        .content("{\"name\":\"X Co\",\"ownerId\":%d}".formatted(clientId)))
                .andExpect(status().isNotFound());

        // A contact whose email is a staff login cannot get portal access. / جهة اتصال إيميلها دخول موظف ما تنعطى دخول.
        jdbc.update("update app_user set username = 'omar@redsea.example' where username = 'khalid'");
        long omar = contactWithEmail(sara, "Omar", noor, "omar@redsea.example");
        mvc.perform(auth(post("/api/contacts/%d/portal-access/invite".formatted(omar)), sara))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    @Test
    void aClientSeesOnlyTheirCompanysIssuedInvoices() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        long redSea = company(sara, "Red Sea");
        String mine = issuedInvoice(sara, noor, 1, "1000");
        String other = issuedInvoice(sara, redSea, 1, "1000");
        draftInvoice(sara, noor, 1, "50");
        String client = clientToken(sara, contactWithEmail(sara, "Reem", noor, "reem@alnoor.example"));

        mvc.perform(auth(get("/api/portal/me"), client))
                .andExpect(jsonPath("$.fullName").value("Reem Client"))
                .andExpect(jsonPath("$.company.name").value("Al Noor"));
        mvc.perform(auth(get("/api/portal/invoices"), client))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].number").value(mine));
        mvc.perform(auth(get("/api/portal/invoices/" + mine), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.total").value(1150.00))
                .andExpect(jsonPath("$.invoice.lines[0].description").value("Consulting"))
                // Internal details stay inside. / التفاصيل الداخلية تبقى داخل.
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.payment.payable").value(true))
                .andExpect(jsonPath("$.payment.methods[0]").value("CARD"));
        mvc.perform(auth(get("/api/portal/invoices/" + other), client)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/portal/invoices/INV-000003"), client)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/portal/invoices/%s/pay".formatted(other)), client).content("{\"method\":\"CARD\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void payingWithTheSandboxCardAndInstalmentLimits() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        // 1000 + 15% = 1150, inside the instalment limits (100–5000). / داخل حدود التقسيط.
        String small = issuedInvoice(sara, noor, 1, "1000");
        // 10000 + 15% = 11500, too big for instalments. / أكبر من حد التقسيط.
        String big = issuedInvoice(sara, noor, 10, "1000");
        String client = clientToken(sara, contactWithEmail(sara, "Reem", noor, "reem@alnoor.example"));

        mvc.perform(auth(get("/api/portal/invoices/" + big), client))
                .andExpect(jsonPath("$.payment.methods.length()").value(2))
                .andExpect(jsonPath("$.payment.instalments.length()").value(0));
        mvc.perform(auth(post("/api/portal/invoices/%s/pay".formatted(big)), client).content("{\"method\":\"TABBY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTALMENTS_NOT_AVAILABLE"));
        mvc.perform(auth(post("/api/portal/invoices/%s/pay".formatted(big)), client).content("{\"method\":\"BANK_TRANSFER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.method").value("Online"));

        // Card: a declined test card leaves the attempt open, then the approved one pays.
        // البطاقة: البطاقة المرفوضة تخلي المحاولة مفتوحة، وبعدها المقبولة تدفع.
        String body = mvc.perform(auth(post("/api/portal/invoices/%s/pay".formatted(big)), client)
                        .content("{\"method\":\"CARD\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.paymentId");
        assertThat((String) JsonPath.read(body, "$.checkoutUrl")).endsWith("/pay/" + paymentId);
        // Pressing pay again reuses the same attempt. / الضغط مرة ثانية يعيد استخدام نفس المحاولة.
        mvc.perform(auth(post("/api/portal/invoices/%s/pay".formatted(big)), client).content("{\"method\":\"CARD\"}"))
                .andExpect(jsonPath("$.paymentId").value(paymentId));
        mvc.perform(get("/api/public/payments/" + paymentId))
                .andExpect(jsonPath("$.invoiceNumber").value(big))
                .andExpect(jsonPath("$.amount").value(11500.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
        String card = "{\"cardNumber\":\"%s\",\"expMonth\":12,\"expYear\":2099,\"cvc\":\"123\",\"cardHolder\":\"Reem\"}";
        mvc.perform(post("/api/public/payments/%s/sandbox".formatted(paymentId)).contentType(MediaType.APPLICATION_JSON)
                        .content(card.formatted("4000 0000 0000 0002")))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("CARD_DECLINED"));
        mvc.perform(post("/api/public/payments/%s/sandbox".formatted(paymentId)).contentType(MediaType.APPLICATION_JSON)
                        .content(card.formatted("4111 1111 1111 1111")))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.cardLast4").value("1111"));
        mvc.perform(auth(get("/api/portal/invoices/" + big), client))
                .andExpect(jsonPath("$.invoice.status").value("PAID"))
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.payable").value(false));
        mvc.perform(auth(post("/api/portal/invoices/%s/pay".formatted(big)), client).content("{\"method\":\"CARD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));
        // Staff see who paid and how. / الموظف يشوف مين دفع وكيف.
        long bigId = jdbc.queryForObject("select id from invoice where number = ?", Long.class, big);
        mvc.perform(auth(get("/api/invoices/" + bigId), sara))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].recordedBy").value("reem@alnoor.example"))
                .andExpect(jsonPath("$.payments[0].cardBrand").value("visa"));

        // Tamara in the sandbox: a 4-part plan, then the client approves. / تمارا تجريبي: خطة 4 دفعات، وبعدها العميل يوافق.
        mvc.perform(auth(get("/api/portal/invoices/" + small), client))
                .andExpect(jsonPath("$.payment.methods.length()").value(4))
                .andExpect(jsonPath("$.payment.instalments.length()").value(4))
                .andExpect(jsonPath("$.payment.instalments[0].amount").value(287.50));
        body = mvc.perform(auth(post("/api/portal/invoices/%s/pay".formatted(small)), client)
                        .content("{\"method\":\"TAMARA\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tamara = JsonPath.read(body, "$.paymentId");
        mvc.perform(post("/api/public/payments/%s/sandbox".formatted(tamara)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approve\":true}"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.instalments.length()").value(4));

        // Voiding the paid invoice refunds it in the sandbox. / إلغاء الفاتورة المدفوعة يسترجعها في الوضع التجريبي.
        long smallId = jdbc.queryForObject("select id from invoice where number = ?", Long.class, small);
        mvc.perform(auth(post("/api/invoices/%d/void".formatted(smallId)), sara)
                        .content("{\"reason\":\"Duplicate\",\"version\":%d}".formatted(versionOf("invoice", smallId))))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"));
        mvc.perform(auth(get("/api/portal/invoices/" + small), client))
                .andExpect(jsonPath("$.invoice.status").value("VOID"))
                .andExpect(jsonPath("$.invoice.voidReason").value("Duplicate"));
    }

    @Test
    void aClientChangesTheirPassword() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        String client = clientToken(sara, contactWithEmail(sara, "Reem", noor, "reem@alnoor.example"));
        mvc.perform(auth(put("/api/portal/password"), client)
                        .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"NewPass@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.currentPassword").value("Wrong"));
        mvc.perform(auth(put("/api/portal/password"), client)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"NewPass@123\"}".formatted(CLIENT_PASSWORD)))
                .andExpect(status().isNoContent());
        login("reem@alnoor.example", "NewPass@123");
    }
}
