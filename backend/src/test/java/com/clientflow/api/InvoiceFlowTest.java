package com.clientflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.clientflow.support.IntegrationTest;

/**
 * Invoices from the staff side: totals, ownership, the status rules, bank transfers and voiding.
 * الفواتير من جهة الموظف: المجاميع، والملكية، وقواعد الحالة، والتحويلات البنكية، والإلغاء.
 */
class InvoiceFlowTest extends IntegrationTest {

    @Test
    void draftTotalsAreComputedOnTheServerWithVat() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        long reem = contact(sara, "Reem", noor);
        long deal = deal(sara, "POS rollout", noor, "50000");
        // The browser sends no totals; whatever it sends besides the lines is ignored.
        // المتصفح ما يرسل مجاميع؛ وأي شي يرسله غير الأسطر ينتجاهل.
        mvc.perform(auth(post("/api/invoices"), sara).content("""
                        {"companyId":%d,"contactId":%d,"dealId":%d,"dueDate":"%s","notes":" Net 14 ",
                         "lines":[{"description":"POS licence","quantity":3,"unitPrice":1000.50},
                                  {"description":"Training","quantity":1,"unitPrice":99.99}]}
                        """.formatted(noor, reem, deal, LocalDate.now().plusDays(14))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoice.number").value("INV-000001"))
                .andExpect(jsonPath("$.invoice.status").value("DRAFT"))
                .andExpect(jsonPath("$.invoice.subtotal").value(3101.49))
                // 15% of 3101.49 = 465.2235 → 465.22. / 15% من 3101.49 = 465.2235 ← 465.22.
                .andExpect(jsonPath("$.invoice.vat").value(465.22))
                .andExpect(jsonPath("$.invoice.total").value(3566.71))
                .andExpect(jsonPath("$.invoice.lines[0].lineTotal").value(3001.50))
                .andExpect(jsonPath("$.invoice.notes").value("Net 14"))
                .andExpect(jsonPath("$.invoice.contact.fullName").value("Reem Test"))
                .andExpect(jsonPath("$.invoice.deal.title").value("POS rollout"))
                .andExpect(jsonPath("$.createdBy.fullName").value("Sara"))
                .andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.payment.payable").value(false));

        mvc.perform(auth(post("/api/invoices"), sara).content("""
                        {"companyId":%d,"dueDate":"%s","lines":[]}
                        """.formatted(noor, LocalDate.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.lines").value("NotEmpty"));
        mvc.perform(auth(post("/api/invoices"), sara).content("""
                        {"companyId":%d,"dueDate":"%s","lines":[{"description":"","quantity":0,"unitPrice":-1}]}
                        """.formatted(noor, LocalDate.now())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['lines[0].description']").value("NotBlank"))
                .andExpect(jsonPath("$.fields['lines[0].quantity']").value("Min"))
                .andExpect(jsonPath("$.fields['lines[0].unitPrice']").value("DecimalMin"));
    }

    @Test
    void onlyTheAccountOwnerOrAnAdminInvoicesAndRecordsMustMatchTheCompany() throws Exception {
        String sara = saraToken();
        String khalid = khalidToken();
        long noor = company(sara, "Al Noor");
        long redSea = company(khalid, "Red Sea");
        long omar = contact(khalid, "Omar", redSea);
        long fleet = deal(khalid, "Fleet", redSea, "1000");
        String lines = "[{\"description\":\"X\",\"quantity\":1,\"unitPrice\":10}]";
        mvc.perform(auth(post("/api/invoices"), khalid).content("""
                        {"companyId":%d,"dueDate":"%s","lines":%s}""".formatted(noor, LocalDate.now(), lines)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
        mvc.perform(auth(post("/api/invoices"), sara).content("""
                        {"companyId":%d,"contactId":%d,"dueDate":"%s","lines":%s}""".formatted(noor, omar, LocalDate.now(), lines)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTACT_COMPANY_MISMATCH"));
        mvc.perform(auth(post("/api/invoices"), sara).content("""
                        {"companyId":%d,"dealId":%d,"dueDate":"%s","lines":%s}""".formatted(noor, fleet, LocalDate.now(), lines)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAL_COMPANY_MISMATCH"));

        // Everyone can read; the admin can manage any account. / الكل يقرأ؛ والمدير يدير أي حساب.
        long id = draftInvoice(sara, noor, 1, "100");
        mvc.perform(auth(get("/api/invoices/" + id), khalid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(false));
        mvc.perform(auth(get("/api/invoices/" + id), adminToken()))
                .andExpect(jsonPath("$.canManage").value(true));
        mvc.perform(auth(delete("/api/invoices/" + id), khalid))
                .andExpect(status().isForbidden());
    }

    @Test
    void anIssuedInvoiceIsLockedAndIsVoidedNotDeleted() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        long id = draftInvoice(sara, noor, 2, "500");
        String edit = """
                {"companyId":%d,"dueDate":"%s","lines":[{"description":"Consulting","quantity":4,"unitPrice":500}],"version":%d}
                """;
        mvc.perform(auth(put("/api/invoices/" + id), sara).content(edit.formatted(noor, LocalDate.now().plusDays(7), 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.total").value(2300.00))
                .andExpect(jsonPath("$.invoice.version").value(1));
        // A stale version is refused. / النسخة القديمة مرفوضة.
        mvc.perform(auth(put("/api/invoices/" + id), sara).content(edit.formatted(noor, LocalDate.now(), 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));

        // A due date in the past cannot be issued. / تاريخ استحقاق في الماضي ما ينصدر.
        jdbc.update("update invoice set due_date = current_date - 1 where id = ?", id);
        mvc.perform(auth(post("/api/invoices/%d/issue".formatted(id)), sara).content("{\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.dueDate").value("FutureOrPresent"));
        jdbc.update("update invoice set due_date = current_date + 7 where id = ?", id);

        mvc.perform(auth(post("/api/invoices/%d/issue".formatted(id)), sara).content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.invoice.issueDate").exists())
                .andExpect(jsonPath("$.payment.payable").value(true))
                .andExpect(jsonPath("$.payment.amountDue").value(2300.00));
        mvc.perform(auth(put("/api/invoices/" + id), sara).content(edit.formatted(noor, LocalDate.now(), 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
        mvc.perform(auth(delete("/api/invoices/" + id), sara))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));

        mvc.perform(auth(post("/api/invoices/%d/void".formatted(id)), sara).content("{\"reason\":\" \",\"version\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").value("NotBlank"));
        mvc.perform(auth(post("/api/invoices/%d/void".formatted(id)), sara).content("{\"reason\":\"Wrong amount\",\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("VOID"))
                .andExpect(jsonPath("$.invoice.voidReason").value("Wrong amount"))
                .andExpect(jsonPath("$.payment.payable").value(false));

        // A draft can be deleted by its account owner. / المسودة يحذفها مالك الحساب.
        long draft = draftInvoice(sara, noor, 1, "10");
        mvc.perform(auth(delete("/api/invoices/" + draft), sara)).andExpect(status().isNoContent());
        // The company now has an invoice, so it cannot be deleted. / الشركة صار لها فاتورة، فما تنحذف.
        mvc.perform(auth(delete("/api/companies/" + noor), adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"))
                .andExpect(jsonPath("$.invoices").value(1));
    }

    @Test
    void aBankTransferPaysTheInvoiceAndVoidingRefundsIt() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        String number = issuedInvoice(sara, noor, 1, "1000");
        long id = jdbc.queryForObject("select id from invoice where number = ?", Long.class, number);

        mvc.perform(auth(post("/api/invoices/%d/bank-transfer".formatted(id)), sara)
                        .content("{\"reference\":\"SNB-123\",\"version\":%d}".formatted(versionOf("invoice", id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("PAID"))
                .andExpect(jsonPath("$.invoice.paidAt").exists())
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.payments[0].provider").value("MANUAL"))
                .andExpect(jsonPath("$.payments[0].recordedBy").value("sara"))
                .andExpect(jsonPath("$.payments[0].amount").value(1150.00));
        // Paid once is enough. / الدفع مرة وحدة يكفي.
        mvc.perform(auth(post("/api/invoices/%d/bank-transfer".formatted(id)), sara)
                        .content("{\"version\":%d}".formatted(versionOf("invoice", id))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOWED"));

        mvc.perform(auth(post("/api/invoices/%d/void".formatted(id)), sara)
                        .content("{\"reason\":\"Client cancelled\",\"version\":%d}".formatted(versionOf("invoice", id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.status").value("VOID"))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"))
                .andExpect(jsonPath("$.payments[0].status").value("REFUNDED"));
    }

    @Test
    void listFiltersAndDashboardBillingNumbers() throws Exception {
        String sara = saraToken();
        long noor = company(sara, "Al Noor");
        long redSea = company(sara, "Red Sea");
        String open = issuedInvoice(sara, noor, 1, "1000");
        String late = issuedInvoice(sara, redSea, 2, "100");
        jdbc.update("update invoice set due_date = current_date - 3 where number = ?", late);
        String paid = issuedInvoice(sara, redSea, 1, "200");
        long paidId = jdbc.queryForObject("select id from invoice where number = ?", Long.class, paid);
        mvc.perform(auth(post("/api/invoices/%d/bank-transfer".formatted(paidId)), sara)
                        .content("{\"version\":%d}".formatted(versionOf("invoice", paidId))))
                .andExpect(status().isOk());
        draftInvoice(sara, noor, 1, "5");

        mvc.perform(auth(get("/api/invoices?overdue=true"), sara))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].number").value(late))
                .andExpect(jsonPath("$.content[0].overdue").value(true));
        mvc.perform(auth(get("/api/invoices?status=ISSUED&companyId=" + noor), sara))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].number").value(open));
        mvc.perform(auth(get("/api/invoices?q=red sea"), sara))
                .andExpect(jsonPath("$.totalElements").value(2));

        // Outstanding = issued and unpaid (1150 + 230); collected = the transfer (230). / المستحق والمحصّل.
        mvc.perform(auth(get("/api/dashboard"), sara))
                .andExpect(jsonPath("$.billing.outstanding").value(1380.00))
                .andExpect(jsonPath("$.billing.overdueCount").value(1))
                .andExpect(jsonPath("$.billing.collectedMonth").value(230.00));
        assertThat(jdbc.queryForObject("select count(*) from invoice", Integer.class)).isEqualTo(4);
    }
}
