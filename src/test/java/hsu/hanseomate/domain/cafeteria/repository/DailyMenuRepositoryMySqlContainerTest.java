package hsu.hanseomate.domain.cafeteria.repository;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MealSection;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
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

/**
 * 실제 MySQL {@code JSON} 컬럼 타입에 대해 dishes 리스트가 값·순서를 보존하며
 * round-trip 되는지 검증한다(H2 만으로는 증명으로 인정하지 않는다).
 * <p>
 * Docker 데몬이 없으면 {@code @Testcontainers(disabledWithoutDocker = true)} 로
 * 자동 스킵된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QueryDslConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class DailyMenuRepositoryMySqlContainerTest {

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
    private DailyMenuRepository dailyMenuRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void dishesJsonList_roundTripsValuesAndOrderThroughRealMysqlJsonColumn() {
        DailyMenu menu = DailyMenu.of(RestaurantType.MAIN_STUDENT, LocalDate.of(2026, 8, 31));
        menu.addMealSection(
                MealTime.LUNCH,
                "1코너",
                5500,
                List.of("쌀밥", "불고기", "김치", "미역국"),
                "1코너 (5.5)\n쌀밥\n불고기\n김치\n미역국"
        );
        DailyMenu saved = dailyMenuRepository.saveAndFlush(menu);
        entityManager.clear();

        DailyMenu reloaded = dailyMenuRepository.findById(saved.getId()).orElseThrow();
        MealSection section = reloaded.getMealSections().get(0);
        assertThat(section.getDishes())
                .containsExactly("쌀밥", "불고기", "김치", "미역국");
    }

    @Test
    void nullPriceAndMultilineKoreanRawText_roundTripThroughRealMysql() {
        DailyMenu menu = DailyMenu.of(RestaurantType.TAEAN_STUDENT, LocalDate.of(2026, 9, 1));
        menu.addMealSection(
                MealTime.DINNER,
                "2코너",
                null,
                Arrays.asList("잔치국수", "단무지"),
                "2코너\n잔치국수\n단무지\n(가격 미정)"
        );
        DailyMenu saved = dailyMenuRepository.saveAndFlush(menu);
        entityManager.clear();

        DailyMenu reloaded = dailyMenuRepository.findById(saved.getId()).orElseThrow();
        MealSection section = reloaded.getMealSections().get(0);
        assertThat(section.getPrice()).isNull();
        assertThat(section.getRawText())
                .isEqualTo("2코너\n잔치국수\n단무지\n(가격 미정)");
        assertThat(section.getDishes()).containsExactly("잔치국수", "단무지");
    }
}
