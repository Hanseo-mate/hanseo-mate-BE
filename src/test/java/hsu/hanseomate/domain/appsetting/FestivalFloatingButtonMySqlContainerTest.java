package hsu.hanseomate.domain.appsetting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hsu.hanseomate.domain.appsetting.dto.FestivalFloatingButtonResponse;
import hsu.hanseomate.domain.appsetting.entity.AppFeatureSetting;
import hsu.hanseomate.domain.appsetting.repository.AppFeatureSettingRepository;
import hsu.hanseomate.domain.appsetting.service.FestivalFloatingButtonService;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;

/** 운영 증분 SQL로 생성한 DB에서 독립 인스턴스의 영속성과 실제 MySQL 잠금을 검증한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FestivalFloatingButtonMySqlContainerTest {

    @SuppressWarnings("resource")
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("festival_setting_test")
            .withUsername("test")
            .withPassword("test");

    private ConfigurableApplicationContext first;
    private ConfigurableApplicationContext second;
    private JdbcTemplate jdbc;
    private String jdbcUrl;
    private String username;
    private String password;
    private boolean ownsContainer;

    @BeforeAll
    void prepareDatabase() throws Exception {
        String localUrl = System.getenv("FESTIVAL_TEST_MYSQL_URL");
        if (localUrl != null) {
            // 로컬 대안도 지정된 테스트 DB만 허용한다. 기존 애플리케이션 DB에는 연결하지 않는다.
            if (!localUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/festival_setting_test(?:\\?.*)?")) {
                throw new IllegalArgumentException("별도 포트의 loopback festival_setting_test DB만 허용합니다.");
            }
            jdbcUrl = localUrl;
            username = System.getenv().getOrDefault("FESTIVAL_TEST_MYSQL_USER", "root");
            password = System.getenv().getOrDefault("FESTIVAL_TEST_MYSQL_PASSWORD", "");
        } else {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker 또는 별도 로컬 MySQL 테스트 인스턴스가 필요합니다.");
            MYSQL.start();
            ownsContainer = true;
            jdbcUrl = MYSQL.getJdbcUrl();
            username = MYSQL.getUsername();
            password = MYSQL.getPassword();
        }
        applyProductionMigration();
    }

    private void applyProductionMigration() throws Exception {
        try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("docs/festival-floating-button-migration-mysql.sql"));
        }
    }

    @AfterAll
    void stopOwnedContainer() {
        if (ownsContainer) {
            MYSQL.stop();
        }
    }

    @BeforeEach
    void startIndependentInstances() {
        first = startInstance();
        second = startInstance();
        jdbc = first.getBean(JdbcTemplate.class);
        jdbc.update("DELETE FROM app_feature_setting_audits");
        jdbc.update("DELETE FROM app_feature_settings");
    }

    @AfterEach
    void stopInstances() {
        if (first != null) {
            first.close();
        }
        if (second != null) {
            second.close();
        }
    }

    @Test
    void migrationValidatesAndStateSurvivesRestartAndMigrationRerun() throws Exception {
        jdbc.update("INSERT INTO app_feature_settings (setting_key) VALUES ('DEFAULT_TEST')");
        assertThat(jdbc.queryForObject("SELECT enabled FROM app_feature_settings WHERE setting_key = 'DEFAULT_TEST'", Boolean.class))
                .isFalse();
        FestivalFloatingButtonResponse saved = service(first).update(true, 7L, "192.0.2.7");
        assertThat(service(second).getSetting()).isEqualTo(saved);

        first.close();
        second.close();
        applyProductionMigration();
        first = startInstance();
        second = startInstance();
        assertThat(service(first).getSetting()).isEqualTo(saved);
        assertThat(service(second).getSetting()).isEqualTo(saved);
        assertThat(first.getBean(JdbcTemplate.class).queryForObject(
                "SELECT COUNT(*) FROM app_feature_setting_audits", Long.class)).isEqualTo(1L);
    }

    @Test
    void concurrentFirstSameValuePatchesCreateOneRowAndOneAudit() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<FestivalFloatingButtonResponse> one = executor.submit(() -> {
                ready.countDown();
                await(start);
                return service(first).update(true, 1L, "192.0.2.1");
            });
            Future<FestivalFloatingButtonResponse> two = executor.submit(() -> {
                ready.countDown();
                await(start);
                return service(second).update(true, 2L, "192.0.2.2");
            });
            await(ready);
            start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS)).isEqualTo(two.get(15, TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_feature_settings", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_feature_setting_audits", Long.class)).isEqualTo(1L);
            assertThat(service(first).getSetting().visible()).isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void lastCommittedExplicitValueWinsEvenWhenFirstRowIsStillUncommitted() throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstWrite = executor.submit(() -> new TransactionTemplate(
                    first.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                        service(first).update(true, 1L, "192.0.2.1");
                        firstWritten.countDown();
                        await(releaseFirst);
                    }));
            await(firstWritten);
            Future<FestivalFloatingButtonResponse> lastWrite = executor.submit(() -> {
                secondStarted.countDown();
                return service(second).update(false, 2L, "192.0.2.2");
            });
            await(secondStarted);
            assertThrows(TimeoutException.class, () -> lastWrite.get(200, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            firstWrite.get(15, TimeUnit.SECONDS);
            assertThat(lastWrite.get(15, TimeUnit.SECONDS).visible()).isFalse();
            assertThat(service(first).getSetting().visible()).isFalse();
            assertThat(service(second).getSetting().visible()).isFalse();
            assertThat(jdbc.queryForList("SELECT new_enabled FROM app_feature_setting_audits ORDER BY id", Boolean.class))
                    .containsExactly(true, false);
            assertThat(jdbc.queryForObject(
                    "SELECT changed_by FROM app_feature_setting_audits ORDER BY id DESC LIMIT 1", Long.class))
                    .isEqualTo(2L);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void realAuditInsertFailureRollsBackSettingUpdate() {
        FestivalFloatingButtonResponse saved = service(first).update(true, 7L, "192.0.2.7");
        jdbc.execute("ALTER TABLE app_feature_setting_audits ADD CONSTRAINT test_reject_false_audit CHECK (new_enabled = b'1')");
        try {
            assertThatThrownBy(() -> service(first).update(false, 8L, "192.0.2.8"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(service(second).getSetting()).isEqualTo(saved);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_feature_setting_audits", Long.class)).isEqualTo(1L);
        } finally {
            jdbc.execute("ALTER TABLE app_feature_setting_audits DROP CHECK test_reject_false_audit");
        }
    }

    private static FestivalFloatingButtonService service(ConfigurableApplicationContext context) {
        return context.getBean(FestivalFloatingButtonService.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트의 다음 단계가 시작되지 않았습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private ConfigurableApplicationContext startInstance() {
        return new SpringApplicationBuilder(PersistenceConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.profiles.active=test",
                        "--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=" + username,
                        "--spring.datasource.password=" + password,
                        "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.jpa.open-in-view=false",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off",
                        "--logging.level.root=WARN"
                );
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = AppFeatureSetting.class)
    @EnableJpaRepositories(basePackageClasses = AppFeatureSettingRepository.class)
    @Import(FestivalFloatingButtonService.class)
    static class PersistenceConfiguration {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
