package com.clientflow.config;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.clientflow.activity.ActivityDtos.ActivityRequest;
import com.clientflow.activity.ActivityService;
import com.clientflow.activity.ActivityType;
import com.clientflow.company.CompanyDtos.CompanyRequest;
import com.clientflow.company.CompanyService;
import com.clientflow.contact.ContactDtos.ContactRequest;
import com.clientflow.contact.ContactService;
import com.clientflow.deal.DealDtos.DealRequest;
import com.clientflow.deal.DealDtos.DealResponse;
import com.clientflow.deal.DealDtos.StageRequest;
import com.clientflow.deal.DealRepository;
import com.clientflow.deal.DealService;
import com.clientflow.deal.DealStage;
import com.clientflow.task.TaskDtos.TaskRequest;
import com.clientflow.task.TaskDtos.TaskResponse;
import com.clientflow.task.TaskPriority;
import com.clientflow.task.TaskService;
import com.clientflow.user.AppUser;
import com.clientflow.user.AppUserRepository;
import com.clientflow.user.Role;

/**
 * Fills an EMPTY database with demo users and a realistic sales pipeline on first start.
 * يعبّي قاعدة بيانات فاضية بمستخدمين تجريبيين وخط مبيعات واقعي عند أول تشغيل.
 *
 * <p>It goes through the real services (not raw SQL inserts), so demo data obeys the same rules as data typed
 * by users: owners, stage history, contact/company consistency. Plain SQL is only used afterwards to move
 * timestamps into the past, so the dashboard has a believable history. It never runs when users already exist,
 * and can be turned off with SEED_DEMO_DATA=false.</p>
 * <p>يستخدم الخدمات الحقيقية (مو إدخال SQL مباشر)، فالبيانات التجريبية تخضع لنفس القواعد: المالكين وسجل المراحل
 * وتوافق جهة الاتصال مع الشركة. والـ SQL المباشر يُستخدم بعدها فقط لإرجاع التواريخ للماضي، حتى يكون للوحة
 * تاريخ منطقي. وما يشتغل أبدًا لو فيه مستخدمين، ويمكن إيقافه بـ SEED_DEMO_DATA=false.</p>
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties properties;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final CompanyService companies;
    private final ContactService contacts;
    private final DealService deals;
    private final DealRepository dealRepository;
    private final TaskService tasks;
    private final ActivityService activities;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    private Instant now;
    private LocalDate today;

    public DemoDataSeeder(AppProperties properties, AppUserRepository users, PasswordEncoder passwordEncoder,
                          CompanyService companies, ContactService contacts, DealService deals,
                          DealRepository dealRepository, TaskService tasks, ActivityService activities,
                          JdbcTemplate jdbc, TransactionTemplate tx) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.companies = companies;
        this.contacts = contacts;
        this.deals = deals;
        this.dealRepository = dealRepository;
        this.tasks = tasks;
        this.activities = activities;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seed().enabled() || users.count() > 0) {
            return;
        }
        // One transaction for the whole seed: either all demo data is created, or none.
        // معاملة وحدة لكل البيانات: يا تنضاف كلها يا ولا شي.
        tx.executeWithoutResult(status -> seed());
        log.info("Demo data created. Log in as 'admin', 'sara' or 'khalid' (passwords from ADMIN_PASSWORD / SALES_PASSWORD).");
    }

    private void seed() {
        now = Instant.now();
        today = LocalDate.now(properties.timeZone());
        AppUser admin = users.save(new AppUser("admin", "System Admin",
                passwordEncoder.encode(properties.seed().adminPassword()), Role.ADMIN));
        AppUser sara = users.save(new AppUser("sara", "Sara Al-Otaibi",
                passwordEncoder.encode(properties.seed().salesPassword()), Role.SALES));
        AppUser khalid = users.save(new AppUser("khalid", "Khalid Al-Ghamdi",
                passwordEncoder.encode(properties.seed().salesPassword()), Role.SALES));

        // The services read the caller from the security context, so the seed acts as the admin
        // (who may assign records to any rep), then as each rep for their own tasks and notes.
        // الخدمات تقرأ المستخدم من سياق الأمان، فالتعبئة تشتغل باسم المدير (اللي يقدر يعيّن السجلات لأي مندوب)،
        // وبعدها باسم كل مندوب لمهامه وملاحظاته.
        asUser("admin", () -> {
            seedData(admin, sara, khalid);
            return null;
        });
    }

    private void seedData(AppUser admin, AppUser sara, AppUser khalid) {
        long noor = company("Al Noor Trading Co.", "Retail", "Riyadh", "+966 11 400 1122", "info@alnoor.example", "alnoor.example", sara);
        long redSea = company("Red Sea Logistics", "Logistics", "Jeddah", "+966 12 610 3344", "contact@redsea.example", "redsea.example", khalid);
        long najd = company("مركز نجد الطبي", "Healthcare", "Riyadh", "+966 11 455 7788", "it@najdmed.example", null, sara);
        long eastern = company("Eastern Petro Services", "Energy", "Dammam", "+966 13 820 5566", "procurement@easternpetro.example", "easternpetro.example", khalid);
        long taiba = company("Taiba Schools", "Education", "Madinah", "+966 14 822 9900", "admin@taiba.example", null, sara);
        long corniche = company("مجموعة الكورنيش للضيافة", "Hospitality", "Jeddah", "+966 12 699 1212", "ops@corniche.example", "corniche.example", khalid);
        long qassim = company("Qassim Dates Co.", "Food", "Buraydah", "+966 16 323 4545", "sales@qassimdates.example", null, sara);
        // A company with no contacts or deals yet, so deleting a company can be tried in the demo.
        // شركة بدون جهات اتصال ولا صفقات، حتى يمكن تجربة حذف شركة في النسخة التجريبية.
        company("Gulf Build Contracting", "Construction", "Khobar", null, null, null, khalid);

        long faisal = contact("Faisal", "Al-Dosari", "faisal@alnoor.example", "+966 50 111 2233", "Operations Manager", noor, sara);
        long reem = contact("Reem", "Al-Shehri", "reem@alnoor.example", null, "Finance Director", noor, sara);
        long omar = contact("Omar", "Bakr", "omar@redsea.example", "+966 55 444 5566", "Fleet Director", redSea, khalid);
        long huda = contact("هدى", "الزهراني", "huda@najdmed.example", "+966 53 222 7788", "IT Manager", najd, sara);
        long majed = contact("Majed", "Al-Mutairi", "majed@easternpetro.example", "+966 56 909 1010", "Procurement Lead", eastern, khalid);
        long lina = contact("Lina", "Hassan", "lina@taiba.example", null, "Principal", taiba, sara);
        long yousef = contact("يوسف", "العمري", "yousef@corniche.example", "+966 54 777 8899", "General Manager", corniche, khalid);
        long nasser = contact("Nasser", "Al-Harbi", "nasser@qassimdates.example", "+966 59 666 1100", "Owner", qassim, sara);
        // A freelancer with no company. / مستقل بدون شركة.
        contact("Ahmad", "Saleh", "ahmad.saleh@example.com", "+966 58 123 4567", "Independent consultant", null, khalid);

        // title, company, contact, owner, amount, expected close (days from today), created (days ago),
        // stages it moved through, closed (days ago) and lost reason.
        // العنوان، الشركة، جهة الاتصال، المالك، القيمة، الإغلاق المتوقع (أيام من اليوم)، الإنشاء (أيام مضت)،
        // المراحل اللي مر فيها، الإغلاق (أيام مضت) وسبب الخسارة.
        long posRollout = deal("POS rollout for 12 branches", noor, faisal, sara, "185000", 20, 35, List.of(DealStage.QUALIFIED, DealStage.PROPOSAL, DealStage.NEGOTIATION), null, null);
        deal("Annual support renewal", noor, reem, sara, "42000", 6, 18, List.of(DealStage.QUALIFIED, DealStage.PROPOSAL), null, null);
        long fleet = deal("Fleet tracking system", redSea, omar, khalid, "260000", 40, 28, List.of(DealStage.QUALIFIED, DealStage.PROPOSAL), null, null);
        deal("Warehouse handheld scanners", redSea, omar, khalid, "64000", 12, 50, List.of(DealStage.QUALIFIED, DealStage.PROPOSAL, DealStage.NEGOTIATION, DealStage.WON), 3, null);
        long clinic = deal("Clinic appointment portal", najd, huda, sara, "120000", 30, 22, List.of(DealStage.QUALIFIED), null, null);
        deal("Network upgrade", najd, huda, sara, "38500", -5, 60, List.of(DealStage.QUALIFIED, DealStage.PROPOSAL, DealStage.WON), 40, null);
        deal("Field safety app", eastern, majed, khalid, "310000", 55, 10, List.of(), null, null);
        deal("Maintenance contract 2025", eastern, majed, khalid, "95000", -20, 75, List.of(DealStage.QUALIFIED, DealStage.PROPOSAL, DealStage.NEGOTIATION, DealStage.LOST), 25, "Chose a cheaper local supplier");
        deal("Student information system", taiba, lina, sara, "150000", 45, 14, List.of(DealStage.QUALIFIED, DealStage.PROPOSAL), null, null);
        deal("Smart classroom pilot", taiba, lina, sara, "27000", 3, 40, List.of(DealStage.QUALIFIED, DealStage.PROPOSAL, DealStage.WON), 8, null);
        long booking = deal("Hotel booking engine", corniche, yousef, khalid, "88000", 25, 12, List.of(DealStage.QUALIFIED), null, null);
        deal("Guest Wi-Fi portal", corniche, yousef, khalid, "19500", -10, 45, List.of(DealStage.QUALIFIED, DealStage.LOST), 15, "Budget frozen until next year");
        deal("E-commerce store", qassim, nasser, sara, "54000", 15, 6, List.of(), null, null);
        deal("Export ERP integration", qassim, nasser, sara, "132000", 5, 65, List.of(DealStage.QUALIFIED, DealStage.PROPOSAL, DealStage.NEGOTIATION, DealStage.WON), 1, null);
        deal("Data centre audit", noor, null, admin, "23000", 9, 4, List.of(DealStage.QUALIFIED), null, null);

        // Tasks: some overdue, some due today, some later, one done, so every task view has content.
        // المهام: متأخرة ومستحقة اليوم ولاحقة ووحدة منجزة، حتى كل عرض للمهام يكون فيه محتوى.
        asUser("sara", () -> {
            task("Send revised POS proposal", -2, TaskPriority.HIGH, null, posRollout);
            task("Call Huda about portal scope", 0, TaskPriority.MEDIUM, null, clinic);
            task("Prepare renewal invoice", 3, TaskPriority.LOW, null, null, noor);
            task("Demo for Taiba IT team", 5, TaskPriority.MEDIUM, null, null, taiba);
            TaskResponse done = task("Confirm meeting with Nasser", -4, TaskPriority.MEDIUM, null, null, qassim);
            tasks.complete(done.id(), done.version());
            note(ActivityType.MEETING, "Workshop with Faisal and the branch managers. They need offline sales mode.", 9, posRollout, null, null);
            note(ActivityType.CALL, "Reem asked for a 3-year price for the support renewal.", 2, null, noor, reem);
            note(ActivityType.EMAIL, "Sent the portal requirements questionnaire to Huda.", 5, clinic, null, null);
            note(ActivityType.NOTE, "Lina prefers Arabic-first screens for the parents' app.", 3, null, taiba, lina);
            return null;
        });
        asUser("khalid", () -> {
            task("Follow up on fleet tracking proposal", -1, TaskPriority.HIGH, null, fleet);
            task("Book site visit at Corniche", 0, TaskPriority.MEDIUM, null, booking);
            task("Send safety app references", 7, TaskPriority.LOW, null, null, eastern);
            // A task Khalid hands to Sara. / مهمة يكلّف فيها خالد سارة.
            task("Introduce Sara to the Qassim export team", 2, TaskPriority.LOW, sara.getId(), null, qassim);
            note(ActivityType.CALL, "Omar confirmed 140 trucks in scope; wants live demo next week.", 4, fleet, null, null);
            note(ActivityType.MEETING, "Kick-off for handheld scanners delivery.", 2, null, redSea, omar);
            note(ActivityType.NOTE, "Majed said the safety app must work without mobile coverage on site.", 1, null, eastern, majed);
            note(ActivityType.EMAIL, "Shared the booking engine pricing with Yousef.", 6, booking, null, null);
            return null;
        });
        task("Review Q4 pipeline with the team", 1, TaskPriority.MEDIUM, null, null, null);
    }

    private long company(String name, String industry, String city, String phone, String email, String website,
                         AppUser owner) {
        return companies.create(new CompanyRequest(name, industry, city, phone, email, website, null,
                owner.getId(), null)).id();
    }

    private long contact(String first, String last, String email, String phone, String title, Long companyId,
                         AppUser owner) {
        return contacts.create(new ContactRequest(first, last, email, phone, title, null, companyId,
                owner.getId(), null)).id();
    }

    /**
     * Creates a deal, walks it through its stages with the real service, then spreads the history over time.
     * ينشئ صفقة ويمشّيها عبر مراحلها بالخدمة الحقيقية، وبعدها يوزّع سجلها على الزمن.
     */
    private long deal(String title, long companyId, Long contactId, AppUser owner, String amount, int closeInDays,
                      int createdDaysAgo, List<DealStage> path, Integer closedDaysAgo, String lostReason) {
        DealResponse deal = deals.create(new DealRequest(title, companyId, contactId, owner.getId(),
                new BigDecimal(amount), today.plusDays(closeInDays), null, null));
        long version = deal.version();
        for (DealStage stage : path) {
            version = deals.changeStage(deal.id(), new StageRequest(stage, lostReason, version)).version();
        }
        // History runs from creation to the close date (or to yesterday for open deals), evenly spaced.
        // السجل يمتد من الإنشاء لتاريخ الإغلاق (أو لأمس للصفقات المفتوحة)، بمسافات متساوية.
        Instant created = now.minus(Duration.ofDays(createdDaysAgo));
        Instant last = closedDaysAgo != null ? now.minus(Duration.ofDays(closedDaysAgo))
                : path.isEmpty() ? created : now.minus(Duration.ofDays(1));
        dealRepository.flush();
        List<Long> changeIds = jdbc.queryForList(
                "select id from deal_stage_change where deal_id = ? order by id", Long.class, deal.id());
        for (int i = 0; i < changeIds.size(); i++) {
            Instant at = changeIds.size() == 1 ? created
                    : created.plus(Duration.between(created, last).multipliedBy(i).dividedBy(changeIds.size() - 1));
            jdbc.update("update deal_stage_change set changed_at = ? where id = ?", Timestamp.from(at), changeIds.get(i));
        }
        jdbc.update("update deal set created_at = ?, updated_at = ?, closed_at = case when closed_at is null then null else cast(? as timestamptz) end where id = ?",
                Timestamp.from(created), Timestamp.from(last), Timestamp.from(last), deal.id());
        return deal.id();
    }

    private TaskResponse task(String title, int dueInDays, TaskPriority priority, Long assigneeId, Long dealId) {
        return task(title, dueInDays, priority, assigneeId, dealId, null);
    }

    private TaskResponse task(String title, int dueInDays, TaskPriority priority, Long assigneeId, Long dealId,
                              Long companyId) {
        TaskResponse task = tasks.create(new TaskRequest(title, null, today.plusDays(dueInDays), priority, assigneeId,
                companyId, dealId, null, null));
        jdbc.update("update task set created_at = ? where id = ?",
                Timestamp.from(now.minus(Duration.ofDays(Math.max(1, 3 - dueInDays)))), task.id());
        return task;
    }

    private void note(ActivityType type, String body, int daysAgo, Long dealId, Long companyId, Long contactId) {
        activities.create(new ActivityRequest(type, body, now.minus(Duration.ofDays(daysAgo)).minus(Duration.ofHours(2)),
                companyId, dealId, contactId));
    }

    /** Runs the block as the given user (seed only). / يشغّل الكود باسم مستخدم معيّن (للتعبئة فقط). */
    private <T> T asUser(String username, Supplier<T> block) {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
        try {
            return block.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

}
