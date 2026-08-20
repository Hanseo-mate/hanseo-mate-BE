package hsu.hanseomate.domain.cafeteria;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CafeteriaApiIntegrationTest.FixedClockConfiguration.class)
class CafeteriaApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanAfterTest() {
        cleanDatabase();
    }

    @Test
    void seosanReturnsOnlyMainStudentMenuInLunchDinnerOrder()
            throws Exception {
        LocalDate menuDate = LocalDate.of(2026, 8, 20);
        insertDailyMenu(1L, "MAIN_STUDENT", menuDate);
        insertMealSection(10L, 1L, "DINNER", "NORMAL");
        insertDish(100L, 10L, "김치볶음밥", true);
        insertMealSection(11L, 1L, "LUNCH", "KOREAN");
        insertDish(101L, 11L, "제육볶음", true);

        insertDailyMenu(2L, "MAIN_STAFF", menuDate);
        insertMealSection(20L, 2L, "LUNCH", "NORMAL");
        insertDish(200L, 20L, "교직원 메뉴", true);

        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("restaurantType", "MAIN_STUDENT")
                        .param("menuDate", "2026-08-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].restaurantType")
                        .value("MAIN_STUDENT"))
                .andExpect(jsonPath("$[0].campus").doesNotExist())
                .andExpect(jsonPath("$[0].menuDate").value("2026-08-20"))
                .andExpect(jsonPath("$[0].dayOfWeek").value("THURSDAY"))
                .andExpect(jsonPath("$[0].mealSections[0].mealTime")
                        .value("LUNCH"))
                .andExpect(jsonPath("$[0].mealSections[0].dishes[0].name")
                        .value("제육볶음"))
                .andExpect(jsonPath("$[0].mealSections[1].mealTime")
                        .value("DINNER"));
    }

    @Test
    void returnsEveryStoredWeekdayFromMondayToFridayWhenDateIsOmitted()
            throws Exception {
        LocalDate monday = LocalDate.of(2026, 8, 17);
        for (int offset = 0; offset < 5; offset++) {
            long dailyMenuId = 100L + offset;
            long lunchSectionId = 1_000L + offset * 10L;
            long dinnerSectionId = lunchSectionId + 1L;
            LocalDate menuDate = monday.plusDays(offset);

            insertDailyMenu(dailyMenuId, "MAIN_STUDENT", menuDate);
            insertMealSection(
                    dinnerSectionId,
                    dailyMenuId,
                    "DINNER",
                    "NORMAL"
            );
            insertDish(
                    2_000L + offset * 10L,
                    dinnerSectionId,
                    menuDate.getDayOfWeek() + " 저녁",
                    true
            );
            insertMealSection(
                    lunchSectionId,
                    dailyMenuId,
                    "LUNCH",
                    "NORMAL"
            );
            insertDish(
                    2_001L + offset * 10L,
                    lunchSectionId,
                    menuDate.getDayOfWeek() + " 점심",
                    true
            );
        }

        insertDailyMenu(200L, "MAIN_STUDENT", monday.minusDays(1));
        insertMealSection(3_000L, 200L, "LUNCH", "NORMAL");
        insertDish(4_000L, 3_000L, "이전 주 메뉴", true);

        insertDailyMenu(201L, "MAIN_STUDENT", monday.plusDays(5));
        insertMealSection(3_001L, 201L, "LUNCH", "NORMAL");
        insertDish(4_001L, 3_001L, "주말 메뉴", true);

        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("restaurantType", "MAIN_STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].menuDate").value("2026-08-17"))
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$[0].mealSections[0].mealTime")
                        .value("LUNCH"))
                .andExpect(jsonPath("$[0].mealSections[1].mealTime")
                        .value("DINNER"))
                .andExpect(jsonPath("$[1].menuDate").value("2026-08-18"))
                .andExpect(jsonPath("$[1].dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$[2].menuDate").value("2026-08-19"))
                .andExpect(jsonPath("$[2].dayOfWeek").value("WEDNESDAY"))
                .andExpect(jsonPath("$[3].menuDate").value("2026-08-20"))
                .andExpect(jsonPath("$[3].dayOfWeek").value("THURSDAY"))
                .andExpect(jsonPath("$[4].menuDate").value("2026-08-21"))
                .andExpect(jsonPath("$[4].dayOfWeek").value("FRIDAY"));
    }

    @Test
    void taeanReturnsOnlyTaeanStudentMenu() throws Exception {
        LocalDate menuDate = LocalDate.of(2026, 8, 20);
        insertDailyMenu(3L, "TAEAN_STUDENT", menuDate);
        insertMealSection(30L, 3L, "LUNCH", "NORMAL");
        insertDish(300L, 30L, "태안 학생 메뉴", true);

        insertDailyMenu(4L, "TAEAN_STAFF", menuDate);
        insertMealSection(40L, 4L, "LUNCH", "NORMAL");
        insertDish(400L, 40L, "태안 교직원 메뉴", true);

        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("restaurantType", "TAEAN_STUDENT")
                        .param("menuDate", "2026-08-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].restaurantType")
                        .value("TAEAN_STUDENT"))
                .andExpect(jsonPath("$[0].campus").doesNotExist())
                .andExpect(jsonPath("$[0].mealSections[0].dishes[0].name")
                        .value("태안 학생 메뉴"));
    }

    @Test
    void returnsNotFoundInsteadOfServerErrorWhenMenuDoesNotExist()
            throws Exception {
        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("restaurantType", "MAIN_STUDENT")
                        .param("menuDate", "2000-01-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/cafeteria/menus"));
    }

    @Test
    void requiresSupportedStudentRestaurantType() throws Exception {
        mockMvc.perform(get("/api/cafeteria/menus"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("restaurantType", "INVALID"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("restaurantType", "MAIN_STAFF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "restaurantType은 MAIN_STUDENT 또는 TAEAN_STUDENT만 사용할 수 있습니다."
                ));

        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("restaurantType", "TAEAN_STAFF"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesOnlyStudentRestaurantTypesInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.parameters[*].name"
                ).value(hasItem("restaurantType")))
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.parameters"
                                + "[?(@.name == 'restaurantType')].required"
                ).value(hasItem(true)))
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.parameters"
                                + "[?(@.name == 'restaurantType')].schema.enum[0]"
                ).value(hasItem("MAIN_STUDENT")))
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.parameters"
                                + "[?(@.name == 'restaurantType')].schema.enum[1]"
                ).value(hasItem("TAEAN_STUDENT")))
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.parameters"
                                + "[?(@.name == 'campus')]"
                ).doesNotExist());
    }

    private void insertDailyMenu(
            long id,
            String restaurantType,
            LocalDate menuDate
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO daily_menus (id, restaurant_type, menu_date)
                        VALUES (?, ?, ?)
                        """,
                id,
                restaurantType,
                Date.valueOf(menuDate)
        );
    }

    private void insertMealSection(
            long id,
            long dailyMenuId,
            String mealTime,
            String menuCategory
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO meal_sections (
                            id, daily_menu_id, meal_time, menu_category
                        ) VALUES (?, ?, ?, ?)
                        """,
                id,
                dailyMenuId,
                mealTime,
                menuCategory
        );
    }

    private void insertDish(
            long id,
            long mealSectionId,
            String name,
            boolean isMainDish
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO dishes (
                            id, meal_section_id, name, is_main_dish
                        ) VALUES (?, ?, ?, ?)
                        """,
                id,
                mealSectionId,
                name,
                isMainDish
        );
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE dishes");
            jdbcTemplate.execute("TRUNCATE TABLE meal_sections");
            jdbcTemplate.execute("TRUNCATE TABLE daily_menus");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-20T03:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}
