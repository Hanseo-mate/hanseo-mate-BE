package hsu.hanseomate.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.auth.exception.InvalidRefreshTokenException;
import hsu.hanseomate.domain.auth.repository.RefreshTokenRepository;
import hsu.hanseomate.domain.auth.service.RefreshTokenService;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import hsu.hanseomate.global.config.JpaAuditingConfig;
import hsu.hanseomate.global.config.QueryDslConfig;
import hsu.hanseomate.global.security.IssuedRefreshToken;
import hsu.hanseomate.global.security.JwtProperties;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        QueryDslConfig.class,
        JpaAuditingConfig.class,
        RefreshTokenConcurrencyMySqlContainerTest.RefreshTokenTestConfig.class
})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenConcurrencyMySqlContainerTest {

    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("hanseomate")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TransactionalRefreshTokenOperations tokenOperations;

    @Test
    void concurrentRotationAllowsOnlyOneRequestAndRevokesReusedFamily()
            throws Exception {
        UserAccount user = userAccountRepository.saveAndFlush(UserAccount.create(
                "refresh-concurrency-user",
                "test-password-hash"
        ));
        IssuedRefreshToken issued = tokenOperations.issue(user);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(
                    () -> rotateAfterSignal(issued.refreshToken(), ready, start)
            );
            Future<Boolean> second = executor.submit(
                    () -> rotateAfterSignal(issued.refreshToken(), ready, start)
            );

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(refreshTokenRepository.findAll())
                .hasSize(2)
                .allMatch(refreshToken -> refreshToken.getRevokedAt() != null);
    }

    private boolean rotateAfterSignal(
            String refreshToken,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 재발급 시작 신호를 받지 못했습니다.");
        }
        try {
            tokenOperations.rotate(refreshToken);
            return true;
        } catch (InvalidRefreshTokenException exception) {
            return false;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RefreshTokenTestConfig {

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "hanseomate-test-jwt-secret-key-32-bytes-minimum",
                    "https://hanseomate.test",
                    3600,
                    2592000
            );
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        RefreshTokenService refreshTokenService(
                RefreshTokenRepository refreshTokenRepository,
                JwtProperties jwtProperties,
                Clock clock
        ) {
            return new RefreshTokenService(
                    refreshTokenRepository,
                    jwtProperties,
                    clock
            );
        }

        @Bean
        TransactionalRefreshTokenOperations transactionalRefreshTokenOperations(
                RefreshTokenService refreshTokenService
        ) {
            return new TransactionalRefreshTokenOperations(refreshTokenService);
        }
    }

    static class TransactionalRefreshTokenOperations {

        private final RefreshTokenService refreshTokenService;

        TransactionalRefreshTokenOperations(
                RefreshTokenService refreshTokenService
        ) {
            this.refreshTokenService = refreshTokenService;
        }

        @Transactional
        public IssuedRefreshToken issue(UserAccount userAccount) {
            return refreshTokenService.issue(userAccount);
        }

        @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
        public IssuedRefreshToken rotate(String refreshToken) {
            return refreshTokenService.rotate(refreshToken).refreshToken();
        }
    }
}
