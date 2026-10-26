package com.clientflow.api;

import static org.assertj.core.api.Assertions.assertThat;

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
                new AppProperties.Seed(true, "Admin@12345", "Sales@12345"));
        return new DemoDataSeeder(enabled, context.getBean(AppUserRepository.class),
                context.getBean(PasswordEncoder.class), context.getBean(CompanyService.class),
                context.getBean(ContactService.class), context.getBean(DealService.class),
                context.getBean(DealRepository.class), context.getBean(TaskService.class),
                context.getBean(ActivityService.class), context.getBean(JdbcTemplate.class),
                context.getBean(TransactionTemplate.class));
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

        assertThat(count("select count(*) from deal")).isEqualTo(15);
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

        // Running again must not duplicate anything. / التشغيل مرة ثانية لازم ما يكرر أي شي.
        seeder().run(null);
        assertThat(count("select count(*) from company")).isEqualTo(8);
    }
}
