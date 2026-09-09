package hsu.hanseomate.domain.appupdate;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hsu.hanseomate.domain.appupdate.config.AppUpdateProperties;
import hsu.hanseomate.domain.appupdate.dto.AppUpdateRequests.*;
import hsu.hanseomate.domain.appupdate.dto.AppUpdateResponses.AdminPolicy;
import hsu.hanseomate.domain.appupdate.entity.AppUpdatePolicy;
import hsu.hanseomate.domain.appupdate.exception.AppUpdateException;
import hsu.hanseomate.domain.appupdate.repository.AppUpdatePolicyRepository;
import hsu.hanseomate.domain.appupdate.service.*;
import hsu.hanseomate.domain.appupdate.support.*;
import hsu.hanseomate.domain.appupdate.type.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.DriverManager;
import java.time.*;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppUpdatePolicyMySqlTest {
    private static final AtomicReference<Instant> TIME = new AtomicReference<>(Instant.parse("2026-09-09T04:00:00.123456Z"));
    private static final AppUpdateRequestContext ADMIN = new AppUpdateRequestContext(7L, "192.0.2.7", "mysql-test", "mysql-request");
    private static final String REASON = "실제 스토어 배포 확인 후 정책 게시";
    @SuppressWarnings("resource")
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("app_update_policy_test").withUsername("test").withPassword("test");
    private ConfigurableApplicationContext first;
    private ConfigurableApplicationContext second;
    private JdbcTemplate jdbc;
    private String url;
    private String username;
    private String password;
    private boolean ownsContainer;
    private TimeZone previousZone;

    @BeforeAll
    void prepare() throws Exception {
        url = System.getenv("APP_UPDATE_TEST_MYSQL_URL");
        if (url == null) {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker 또는 별도 로컬 MySQL 테스트 인스턴스가 필요합니다.");
            MYSQL.start();
            ownsContainer = true;
            url = MYSQL.getJdbcUrl();
            username = MYSQL.getUsername();
            password = MYSQL.getPassword();
        } else {
            if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:(?!3306/)[0-9]+/app_update_policy_test(?:\\?.*)?")) {
                throw new IllegalArgumentException("별도 loopback 포트의 app_update_policy_test DB만 허용합니다.");
            }
            username = System.getenv().getOrDefault("APP_UPDATE_TEST_MYSQL_USER", "root");
            password = System.getenv().getOrDefault("APP_UPDATE_TEST_MYSQL_PASSWORD", "");
        }
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        migrate();
        first = instance();
        second = instance();
        jdbc = first.getBean(JdbcTemplate.class);
    }

    @AfterAll
    void close() {
        if (first != null) first.close();
        if (second != null) second.close();
        if (ownsContainer) MYSQL.stop();
        if (previousZone != null) TimeZone.setDefault(previousZone);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM app_update_audits");
        jdbc.update("DELETE FROM app_update_commands");
        jdbc.update("DELETE FROM app_update_policies");
        jdbc.update("DELETE FROM app_update_platform_locks");
        TIME.set(Instant.parse("2026-09-09T04:00:00.123456Z"));
    }

    @Test
    void migrationRerunAndRestartPreservePolicyAuditReplayAndUtcInstants() throws Exception {
        AdminPolicy draft = create(first, AppPlatform.IOS);
        Publish request = immediate(draft);
        AdminPolicy published = service(first).publish(draft.id(), request, "persistent", ADMIN);
        assertThat(service(second).get(draft.id())).isEqualTo(published);
        assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(effective_at, '%Y-%m-%d %H:%i:%s.%f') FROM app_update_policies WHERE id = ?",
                String.class, draft.id())).isEqualTo("2026-09-09 04:00:00.123456");
        first.close();
        second.close();
        migrate();
        first = instance();
        second = instance();
        jdbc = first.getBean(JdbcTemplate.class);
        assertThat(service(first).get(draft.id())).isEqualTo(published);
        assertThat(service(second).publish(draft.id(), request, "persistent", ADMIN)).isEqualTo(published);
        assertThat(count("app_update_audits", "success = true")).isEqualTo(2);
    }

    @Test
    void concurrentSameIdempotencyKeyReturnsOneCommitToBothInstances() throws Exception {
        AdminPolicy draft = create(first, AppPlatform.IOS);
        Publish request = immediate(draft);
        List<AdminPolicy> results = concurrently(
                () -> service(first).publish(draft.id(), request, "duplicate", ADMIN),
                () -> service(second).publish(draft.id(), request, "duplicate", ADMIN));
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(count("app_update_policies", "status = 'ACTIVE'")).isEqualTo(1);
        assertThat(count("app_update_commands", "1=1")).isEqualTo(1);
        assertThat(count("app_update_audits", "event_type = 'PUBLISH' AND success = true")).isEqualTo(1);
    }

    @Test
    void concurrentDifferentKeysWithSameRevisionProduceOneConflict() throws Exception {
        AdminPolicy draft = create(first, AppPlatform.IOS);
        Publish request = immediate(draft);
        List<Object> results = concurrently(
                () -> outcome(() -> service(first).publish(draft.id(), request, "one", ADMIN)),
                () -> outcome(() -> service(second).publish(draft.id(), request, "two", ADMIN)));
        assertThat(results.stream().filter(AdminPolicy.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter("CONFLICT"::equals)).hasSize(1);
        assertThat(count("app_update_policies", "status = 'ACTIVE'")).isEqualTo(1);
        assertThat(count("app_update_commands", "1=1")).isEqualTo(1);
    }

    @Test
    void concurrentDifferentPoliciesSerializeTheirActivation() throws Exception {
        AdminPolicy one = create(first, AppPlatform.IOS);
        AdminPolicy two = create(first, AppPlatform.IOS);
        List<AdminPolicy> results = concurrently(
                () -> service(first).publish(one.id(), immediate(one), "one", ADMIN),
                () -> service(second).publish(two.id(), immediate(two), "two", ADMIN));
        assertThat(results).hasSize(2);
        assertThat(count("app_update_policies", "status = 'ACTIVE'")).isEqualTo(1);
        assertThat(count("app_update_policies", "status = 'SUPERSEDED'")).isEqualTo(1);
        assertThat(count("app_update_audits", "event_type = 'PUBLISH' AND success = true")).isEqualTo(2);
    }

    @Test
    void sameKeyAcrossPlatformsIsRejectedWithoutSecondMutation() throws Exception {
        AdminPolicy ios = create(first, AppPlatform.IOS);
        AdminPolicy android = create(first, AppPlatform.ANDROID);
        List<Object> results = concurrently(
                () -> outcome(() -> service(first).publish(ios.id(), immediate(ios), "global-key", ADMIN)),
                () -> outcome(() -> service(second).publish(android.id(), immediate(android), "global-key", ADMIN)));
        assertThat(results.stream().filter(AdminPolicy.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter("CONFLICT"::equals)).hasSize(1);
        assertThat(count("app_update_policies", "status = 'DRAFT'")).isEqualTo(1);
        assertThat(count("app_update_commands", "1=1")).isEqualTo(1);
    }

    @Test
    void concurrentSchedulersActivateExactlyOnce() throws Exception {
        AdminPolicy draft = create(first, AppPlatform.IOS);
        service(first).publish(draft.id(), new Publish(draft.revision(), PublishMode.SCHEDULED,
                TIME.get().plusSeconds(5), true, REASON), "schedule", ADMIN);
        TIME.set(TIME.get().plusSeconds(5));
        List<Boolean> results = concurrently(
                () -> service(first).activateDue(draft.id(), AppUpdateRequestContext.scheduler()),
                () -> service(second).activateDue(draft.id(), AppUpdateRequestContext.scheduler()));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(count("app_update_audits", "event_type = 'ACTIVATE' AND success = true")).isEqualTo(1);
    }

    @Test
    void concurrentDraftEditsRequireFreshRevision() throws Exception {
        AdminPolicy draft = create(first, AppPlatform.IOS);
        Update request = new Update(draft.revision(), "1.4", 31L, true, 28L, true,
                draft.storeUrl(), draft.title(), draft.message(), REASON);
        List<Object> results = concurrently(
                () -> outcome(() -> service(first).update(draft.id(), request, ADMIN)),
                () -> outcome(() -> service(second).update(draft.id(), request, ADMIN)));
        assertThat(results.stream().filter(AdminPolicy.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter("CONFLICT"::equals)).hasSize(1);
        assertThat(service(first).get(draft.id()).revision()).isEqualTo(2);
    }

    @Test
    void realAuditInsertFailureRollsBackAllPolicyStateAndAllowsRetry() {
        AdminPolicy initial = create(first, AppPlatform.IOS);
        service(first).publish(initial.id(), immediate(initial), "initial", ADMIN);
        AdminPolicy draft = create(first, AppPlatform.IOS);
        jdbc.execute("ALTER TABLE app_update_audits ADD CONSTRAINT test_fail_publish CHECK (event_type <> 'PUBLISH' OR policy_id <> " + draft.id() + ")");
        try {
            assertDatabaseConstraintFailure(() -> service(first).publish(draft.id(), immediate(draft), "retry", ADMIN), 3819);
            assertThat(service(second).get(initial.id()).status()).isEqualTo(AppUpdateStatus.ACTIVE);
            assertThat(service(second).get(draft.id()).status()).isEqualTo(AppUpdateStatus.DRAFT);
            assertThat(count("app_update_commands", "idempotency_key = 'retry'")).isZero();
        } finally {
            jdbc.execute("ALTER TABLE app_update_audits DROP CHECK test_fail_publish");
        }
        assertThat(service(first).publish(draft.id(), immediate(draft), "retry", ADMIN).status()).isEqualTo(AppUpdateStatus.ACTIVE);
    }

    @Test
    void realConstraintsRejectInvalidMinimumAndDuplicateActiveSlot() {
        AdminPolicy active = create(first, AppPlatform.IOS);
        service(first).publish(active.id(), immediate(active), "active", ADMIN);
        AdminPolicy draft = create(first, AppPlatform.IOS);
        assertDatabaseConstraintFailure(() -> jdbc.update(
                "UPDATE app_update_policies SET minimum_supported_build = 31 WHERE id = ?", active.id()), 3819);
        assertDatabaseConstraintFailure(() -> jdbc.update(
                "UPDATE app_update_policies SET force_update_enabled = false WHERE id = ?", active.id()), 3819);
        assertDatabaseConstraintFailure(() -> jdbc.update(
                "UPDATE app_update_policies SET active_platform = NULL WHERE id = ?", active.id()), 3819);
        assertDatabaseConstraintFailure(() -> jdbc.update("""
                UPDATE app_update_policies SET status = 'ACTIVE', active_platform = 'IOS',
                effective_at = CURRENT_TIMESTAMP(6), published_at = CURRENT_TIMESTAMP(6), published_by = 7,
                store_availability_confirmed_at = CURRENT_TIMESTAMP(6), store_availability_confirmed_by = 7 WHERE id = ?
                """, draft.id()), 1062);
        assertThat(service(second).get(active.id()).status()).isEqualTo(AppUpdateStatus.ACTIVE);
    }

    private void migrate() throws Exception {
        try (var connection = DriverManager.getConnection(url, username, password)) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("docs/app-update-policy-migration-mysql.sql"));
        }
    }

    private static void assertDatabaseConstraintFailure(Runnable operation, int mysqlError) {
        Throwable failure = catchThrowable(operation::run);
        assertThat(failure).isInstanceOf(DataAccessException.class);
        Throwable rootCause = failure;
        while (rootCause.getCause() != null) rootCause = rootCause.getCause();
        assertThat(rootCause).isInstanceOf(java.sql.SQLException.class);
        assertThat(((java.sql.SQLException) rootCause).getErrorCode()).isEqualTo(mysqlError);
    }

    private AdminPolicy create(ConfigurableApplicationContext context, AppPlatform platform) {
        return service(context).create(new Create(platform, "1.3", 30L, true, 28L, true,
                platform == AppPlatform.IOS ? "https://apps.apple.com/app/id1234567890"
                        : "https://play.google.com/store/apps/details?id=com.hanseomate.app",
                "업데이트가 필요해요", "최신 버전으로 업데이트해 주세요.", REASON), ADMIN);
    }

    private Publish immediate(AdminPolicy policy) {
        return new Publish(policy.revision(), PublishMode.IMMEDIATE, null, true, REASON);
    }

    private AppUpdatePolicyService service(ConfigurableApplicationContext context) {
        return context.getBean(AppUpdatePolicyService.class);
    }

    private long count(String table, String where) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
    }

    private Object outcome(Supplier<AdminPolicy> operation) {
        try { return operation.get(); }
        catch (AppUpdateException conflict) {
            if (conflict.getStatus().value() != 409) throw conflict;
            return "CONFLICT";
        }
    }

    private <T> List<T> concurrently(Supplier<T> one, Supplier<T> two) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> a = executor.submit(() -> { ready.countDown(); await(start); return one.get(); });
            Future<T> b = executor.submit(() -> { ready.countDown(); await(start); return two.get(); });
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private ConfigurableApplicationContext instance() {
        return new SpringApplicationBuilder(PersistenceConfig.class).web(WebApplicationType.NONE).run(
                "--spring.profiles.active=test", "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + username, "--spring.datasource.password=" + password,
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.show-sql=false",
                "--app.updates.ios-app-store-id=1234567890", "--spring.main.banner-mode=off",
                "--logging.level.root=WARN");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = AppUpdatePolicy.class)
    @EnableJpaRepositories(basePackageClasses = AppUpdatePolicyRepository.class)
    @Import({AppUpdatePolicyService.class, AppUpdateAuditService.class, AppUpdatePolicyOperations.class,
            AppUpdateValidator.class, AppUpdateProperties.class})
    static class PersistenceConfig {
        @Bean Clock clock() {
            return new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return Clock.fixed(TIME.get(), zone); }
                @Override public Instant instant() { return TIME.get(); }
            };
        }
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }
}
