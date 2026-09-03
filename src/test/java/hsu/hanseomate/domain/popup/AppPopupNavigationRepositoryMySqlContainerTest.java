package hsu.hanseomate.domain.popup;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.popup.entity.AppPopup;
import hsu.hanseomate.domain.popup.model.PopupNavigation;
import hsu.hanseomate.domain.popup.repository.AppPopupRepository;
import hsu.hanseomate.domain.popup.type.PopupNavigationType;
import hsu.hanseomate.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class AppPopupNavigationRepositoryMySqlContainerTest {

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
    private AppPopupRepository appPopupRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void navigationJsonRoundTripsThroughRealMySqlJsonColumn() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("noticeId", 123L);
        params.put("noticeType", "ACADEMIC");

        AppPopup saved = appPopupRepository.saveAndFlush(AppPopup.create(
                "공지 이동",
                "내용",
                null,
                new PopupNavigation(
                        (short) 1,
                        PopupNavigationType.NOTICE_DETAIL,
                        params
                ),
                true,
                null,
                null,
                0
        ));
        entityManager.clear();

        PopupNavigation navigation = appPopupRepository.findById(saved.getId())
                .orElseThrow()
                .navigation();

        assertThat(navigation.schemaVersion()).isEqualTo((short) 1);
        assertThat(navigation.type()).isEqualTo(PopupNavigationType.NOTICE_DETAIL);
        assertThat(((Number) navigation.params().get("noticeId")).longValue())
                .isEqualTo(123L);
        assertThat(navigation.params().get("noticeType")).isEqualTo("ACADEMIC");
    }
}
