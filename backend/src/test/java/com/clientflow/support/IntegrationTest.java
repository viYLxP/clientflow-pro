package com.clientflow.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.clientflow.user.AppUser;
import com.clientflow.user.AppUserRepository;
import com.clientflow.user.Role;

/**
 * Base class for integration tests: real Spring context + a REAL PostgreSQL in Docker (Testcontainers).
 * الكلاس الأساسي لاختبارات التكامل: سياق Spring حقيقي + PostgreSQL حقيقي داخل Docker (Testcontainers).
 *
 * <p>We test against PostgreSQL instead of an in-memory database so the Flyway migrations, CHECK constraints
 * and cascades are exactly the ones used in production.</p>
 * <p>نختبر على PostgreSQL بدل قاعدة بيانات في الذاكرة حتى تكون ترحيلات Flyway وقيود CHECK والحذف المتسلسل
 * هي نفسها اللي في الإنتاج.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    // One container shared by all test classes (started once), which keeps the suite fast.
    // حاوية وحدة مشتركة بين كل كلاسات الاختبار (تشتغل مرة وحدة)، وهذا يخلي الاختبارات سريعة.
    // @ServiceConnection points spring.datasource at this container automatically.
    // @ServiceConnection يوجّه إعدادات قاعدة البيانات لهذي الحاوية تلقائيًا.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    protected static final String ADMIN_PASSWORD = "Admin@12345";
    protected static final String SALES_PASSWORD = "Sales@12345";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Wipes all data before each test so tests never depend on each other's leftovers.
     * Users: admin, and two sales reps (sara, khalid) so ownership rules can be tested.
     * يمسح كل البيانات قبل كل اختبار حتى ما يعتمد أي اختبار على بقايا اختبار ثاني.
     * المستخدمين: مدير ومندوبين (sara و khalid) حتى نختبر قواعد الملكية.
     */
    @BeforeEach
    void resetDatabase() {
        jdbc.execute("""
                truncate table activity, task, deal_stage_change, deal, contact, company, app_user
                restart identity cascade
                """);
        users.save(new AppUser("admin", "Admin", passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN));
        users.save(new AppUser("sara", "Sara", passwordEncoder.encode(SALES_PASSWORD), Role.SALES));
        users.save(new AppUser("khalid", "Khalid", passwordEncoder.encode(SALES_PASSWORD), Role.SALES));
    }

    /**
     * Logs in through the real endpoint and returns a JWT.
     * يسجّل دخول عبر الرابط الحقيقي ويرجّع رمز JWT.
     */
    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected String adminToken() throws Exception {
        return login("admin", ADMIN_PASSWORD);
    }

    protected String saraToken() throws Exception {
        return login("sara", SALES_PASSWORD);
    }

    protected String khalidToken() throws Exception {
        return login("khalid", SALES_PASSWORD);
    }

    protected long userId(String username) {
        return jdbc.queryForObject("select id from app_user where username = ?", Long.class, username);
    }

    /**
     * Adds the bearer token and JSON content type to a request.
     * يضيف رمز الدخول ونوع المحتوى JSON للطلب.
     */
    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /**
     * Performs a POST that must succeed and returns the "id" field of the response.
     * ينفذ POST لازم ينجح ويرجّع حقل "id" من الرد.
     */
    protected long createId(String url, String json, String token) throws Exception {
        String body = mvc.perform(auth(post(url), token).content(json))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Current optimistic-lock version of a row. / رقم النسخة الحالي لصف. */
    protected long versionOf(String table, long id) {
        return jdbc.queryForObject("select version from " + table + " where id = ?", Long.class, id);
    }

    // ----- Fixture helpers / دوال تجهيز البيانات -----

    protected long company(String token, String name) throws Exception {
        return createId("/api/companies", "{\"name\":\"%s\"}".formatted(name), token);
    }

    protected long contact(String token, String firstName, Long companyId) throws Exception {
        return createId("/api/contacts", """
                {"firstName":"%s","lastName":"Test","companyId":%s}
                """.formatted(firstName, companyId), token);
    }

    protected long deal(String token, String title, long companyId, String amount) throws Exception {
        return createId("/api/deals", """
                {"title":"%s","companyId":%d,"amount":%s}
                """.formatted(title, companyId, amount), token);
    }
}
