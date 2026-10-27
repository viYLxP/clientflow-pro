package com.clientflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.clientflow.activity.ActivityService;
import com.clientflow.company.CompanyService;
import com.clientflow.config.AppProperties;
import com.clientflow.config.DemoDataSeeder;
import com.clientflow.contact.ContactService;
import com.clientflow.deal.DealRepository;
import com.clientflow.deal.DealService;
import com.clientflow.inquiry.InquiryService;
import com.clientflow.invoice.InvoiceService;
import com.clientflow.payment.PaymentService;
import com.clientflow.portal.PortalAccessService;
import com.clientflow.support.IntegrationTest;
import com.clientflow.task.TaskService;
import com.clientflow.user.AppUserRepository;

/**
 * The demo data goes through the real services, so it must obey the same rules as user data,
 * and its back-dated history must still read in the right order.
 * البيانات التجريبية تمر عبر الخدمات الحقيقية، فلازم تلتزم بنفس القواعد،
 * وسجلها اللي رجّعنا تواريخه لازم يبقى مرتب صح.
 */
class DemoDataSeederTest extends IntegrationTest {

    @Autowired ApplicationContext context;
    @Autowired AppProperties properties;

    private DemoDataSeeder seeder() {
        AppProperties enabled = new AppProperties(properties.jwt(), properties.cors(), properties.timeZone(),
                properties.vatRate(), new AppProperties.Seed(true, "Admin@12345", "Sales@12345", "Client@12345"),
                properties.rateLimit(), properties.payments(), properties.portal());
        return new DemoDataSeeder(enabled, context.getBean(AppUserRepository.class),
                context.getBean(PasswordEncoder.class), context.getBean(CompanyService.class),
                context.getBean(ContactService.class), context.getBean(DealService.class),
                context.getBean(DealRepository.class), context.getBean(TaskService.class),
                context.getBean(ActivityService.class), context.getBean(InvoiceService.class),
                context.getBean(PaymentService.class), context.getBean(PortalAccessService.class),
                context.getBean(InquiryService.class), context.getBean(JdbcTemplate.class),
                context.getBean(TransactionTemplate.class), context.getBean(EntityManager.class));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void seedProducesConsistentChronologicalData() throws Exception {
        // The base class created test users; the seeder only runs on an empty user table.
        // الكلاس الأساسي أنشأ مستخدمين للاختبار؛ والـ seeder يشتغل فقط لما يكون جدول المستخدمين فاضي.
        jdbc.execute("truncate table app_user restart identity cascade");
        seeder().run(null);

        // 15 pipeline deals, plus one created by converting a contact-form inquiry.
        // 15 صفقة في خط المبيعات، وزيادة وحدة انعملت من تحويل استفسار من نموذج التواصل.
        assertThat(count("select count(*) from deal")).isEqualTo(16);
        // Every pipeline stage has at least one deal, so the board and the reports are never half empty.
        // كل مرحلة فيها صفقة على الأقل، حتى اللوحة والتقارير ما تكون نصها فاضي.
        assertThat(count("select count(distinct stage) from deal")).isEqualTo(6);

        // The last history row of each deal is its current stage. / آخر صف في سجل كل صفقة هو مرحلتها الحالية.
        assertThat(count("""
                select count(*) from deal d
                where d.stage <> (select c.to_stage from deal_stage_change c where c.deal_id = d.id
                                  order by c.changed_at desc, c.id desc limit 1)
                """)).isZero();
        // History is in time order and starts when the deal was created; a closed deal closed at its last change.
        // السجل بترتيب زمني ويبدأ مع إنشاء الصفقة؛ والصفقة المغلقة انغلقت مع آخر تغيير.
        assertThat(count("""
                select count(*) from (
                  select id, changed_at < lag(changed_at) over (partition by deal_id order by id) as backwards
                  from deal_stage_change) h
                where backwards
                """)).isZero();
        assertThat(count("""
                select count(*) from deal d
                where d.created_at <> (select min(changed_at) from deal_stage_change c where c.deal_id = d.id)
                   or (d.closed_at is not null and d.closed_at <> (select max(changed_at) from deal_stage_change c where c.deal_id = d.id))
                   or d.closed_at > now()
                """)).isZero();
        // Tasks were created before they fell due, and no activity is in the future.
        // المهام انشأت قبل موعدها، وما فيه نشاط في المستقبل.
        assertThat(count("select count(*) from task where created_at > now() or (status = 'DONE') <> (completed_at is not null)")).isZero();
        assertThat(count("select count(*) from activity where occurred_at > now()")).isZero();
        // Contacts on deals and activities work at the linked company. / جهات الاتصال على الصفقات والأنشطة من نفس الشركة.
        assertThat(count("""
                select count(*) from deal d join contact c on c.id = d.contact_id where c.company_id <> d.company_id
                """)).isZero();
        assertThat(count("""
                select count(*) from activity a join contact c on c.id = a.contact_id where c.company_id <> a.company_id
                """)).isZero();

        // The demo logins work, and the dashboard shows overdue work for Sara.
        // حسابات التجربة تشتغل، ولوحة سارة فيها مهام متأخرة.
        login("sara", "Sales@12345");
        login("khalid", "Sales@12345");
        assertThat(count("""
                select count(*) from task t join app_user u on u.id = t.assignee_id
                where u.username = 'sara' and t.status = 'OPEN' and t.due_date < current_date
                """)).isPositive();

        // Invoices in every state, with totals that add up and VAT at 15%.
        // فواتير بكل الحالات، ومجاميعها مضبوطة والضريبة 15%.
        assertThat(count("select count(*) from invoice")).isEqualTo(9);
        assertThat(count("select count(distinct status) from invoice")).isEqualTo(4);
        assertThat(count("""
                select count(*) from invoice i
                where i.subtotal <> (select sum(l.line_total) from invoice_line l where l.invoice_id = i.id)
                   or i.vat <> round(i.subtotal * 0.15, 2)
                """)).isZero();
        assertThat(count("select count(*) from invoice where status = 'ISSUED' and due_date < current_date")).isEqualTo(1);
        // Each paid invoice has exactly one paid payment: the test card and the bank transfer.
        // كل فاتورة مدفوعة لها دفعة مدفوعة وحدة: البطاقة التجريبية والتحويل البنكي.
        assertThat(jdbc.queryForList("""
                select p.method from payment p join invoice i on i.id = p.invoice_id
                where i.status = 'PAID' and p.status = 'PAID' order by p.method
                """, String.class)).containsExactly("BANK_TRANSFER", "CARD");
        assertThat(count("select count(*) from invoice where issued_at > now() or paid_at > now()")).isZero();

        // The demo client signs in; the second contact is invited but has not set a password yet.
        // العميل التجريبي يدخل؛ وجهة الاتصال الثانية انْدعت بس لسا ما اختارت كلمة مرور.
        login("faisal@alnoor.example", "Client@12345");
        assertThat(count("select count(*) from app_user where role = 'CLIENT' and not active and invite_token_hash is not null"))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("select status from inquiry order by status", String.class))
                .containsExactly("CONVERTED", "DISMISSED", "NEW", "NEW", "NEW");

        // Running again must not duplicate anything. / التشغيل مرة ثانية لازم ما يكرر أي شي.
        seeder().run(null);
        assertThat(count("select count(*) from company")).isEqualTo(9);
    }
}
