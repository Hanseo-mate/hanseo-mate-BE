package hsu.hanseomate.domain.cafeteria;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.repository.DailyMenuRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
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

    @Autowired
    private DailyMenuRepository dailyMenuRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void cleanBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanAfterTest() {
        cleanDatabase();
    }

    @Test
    void anonymousReceivesBothRestaurantBucketsInStableOrder()
            throws Exception {
        LocalDate menuDate = LocalDate.of(2026, 8, 20);
        saveDailyMenu("MAIN_STUDENT", menuDate,
                new SectionFixture(MealTime.DINNER, "석식코너", 6000,
                        List.of("김치볶음밥"), "석식코너 (6.0)\n김치볶음밥"),
                new SectionFixture(MealTime.LUNCH, "한식코너", 5500,
                        List.of("제육볶음", "된장국"), "한식코너 (5.5)\n제육볶음\n된장국"));

        saveDailyMenu("TAEAN_STUDENT", menuDate,
                new SectionFixture(MealTime.LUNCH, "일품코너", 5000,
                        List.of("태안 학생 메뉴"), "일품코너 (5.0)\n태안 학생 메뉴"));

        saveDailyMenu("MAIN_STAFF", menuDate,
                new SectionFixture(MealTime.LUNCH, "교직원코너", 7000,
                        List.of("교직원 메뉴"), "교직원코너 (7.0)\n교직원 메뉴"));

        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("menuDate", "2026-08-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value(nullValue()))
                .andExpect(jsonPath("$.restaurants.length()").value(2))
                .andExpect(jsonPath("$.restaurants[0].restaurantType")
                        .value("MAIN_STUDENT"))
                .andExpect(jsonPath("$.restaurants[0].dailyMenus.length()")
                        .value(1))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[0].menuDate"
                ).value("2026-08-20"))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[0].dayOfWeek"
                ).value("THURSDAY"))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[0]"
                                + ".mealSections[0].mealTime"
                ).value("LUNCH"))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[0]"
                                + ".mealSections[0].cornerName"
                ).value("한식코너"))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[0]"
                                + ".mealSections[0].price"
                ).value(5500))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[0]"
                                + ".mealSections[0].dishes[0]"
                ).value("제육볶음"))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[0]"
                                + ".mealSections[0].dishes[1]"
                ).value("된장국"))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[0]"
                                + ".mealSections[0].rawText"
                ).value("한식코너 (5.5)\n제육볶음\n된장국"))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[0]"
                                + ".mealSections[1].mealTime"
                ).value("DINNER"))
                .andExpect(jsonPath("$.restaurants[1].restaurantType")
                        .value("TAEAN_STUDENT"))
                .andExpect(jsonPath("$.restaurants[1].dailyMenus.length()")
                        .value(1))
                .andExpect(jsonPath(
                        "$.restaurants[1].dailyMenus[0]"
                                + ".mealSections[0].dishes[0]"
                ).value("태안 학생 메뉴"));
    }

    @Test
    void authenticatedUserReceivesPreferredRestaurantType() throws Exception {
        UserAccount userAccount = UserAccount.create(
                "cafeteria-user",
                "encoded-password"
        );
        userAccount.changePreferredRestaurantType(
                RestaurantType.TAEAN_STUDENT
        );
        userAccountRepository.saveAndFlush(userAccount);

        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("menuDate", "2026-08-20")
                        .with(jwt().jwt(token -> token
                                .subject(userAccount.getId().toString())
                                .claim("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value("TAEAN_STUDENT"))
                .andExpect(jsonPath("$.restaurants.length()").value(2))
                .andExpect(jsonPath("$.restaurants[0].restaurantType")
                        .value("MAIN_STUDENT"))
                .andExpect(jsonPath("$.restaurants[1].restaurantType")
                        .value("TAEAN_STUDENT"));
    }

    @Test
    void invalidBearerTokenIsRejectedInsteadOfFallingBackToAnonymous()
            throws Exception {
        mockMvc.perform(get("/api/cafeteria/menus")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-token"
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsEveryStoredWeekdayForBothRestaurantsWhenDateIsOmitted()
            throws Exception {
        LocalDate monday = LocalDate.of(2026, 8, 17);
        for (int offset = 0; offset < 5; offset++) {
            LocalDate menuDate = monday.plusDays(offset);
            saveDailyMenu("MAIN_STUDENT", menuDate,
                    new SectionFixture(MealTime.LUNCH, "코너", 5000,
                            List.of(menuDate.getDayOfWeek() + " 점심"), "raw"));
        }

        saveDailyMenu("TAEAN_STUDENT", monday,
                new SectionFixture(MealTime.LUNCH, "코너", 5000,
                        List.of("태안 월요일"), "raw"));
        saveDailyMenu("TAEAN_STUDENT", monday.plusDays(4),
                new SectionFixture(MealTime.DINNER, "코너", 5000,
                        List.of("태안 금요일"), "raw"));

        saveDailyMenu("MAIN_STUDENT", monday.minusDays(1),
                new SectionFixture(MealTime.LUNCH, "코너", 5000,
                        List.of("이전 주 메뉴"), "raw"));
        saveDailyMenu("MAIN_STUDENT", monday.plusDays(5),
                new SectionFixture(MealTime.LUNCH, "코너", 5000,
                        List.of("주말 메뉴"), "raw"));

        mockMvc.perform(get("/api/cafeteria/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurants[0].dailyMenus.length()")
                        .value(5))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[0].menuDate"
                ).value("2026-08-17"))
                .andExpect(jsonPath(
                        "$.restaurants[0].dailyMenus[4].menuDate"
                ).value("2026-08-21"))
                .andExpect(jsonPath("$.restaurants[1].dailyMenus.length()")
                        .value(2))
                .andExpect(jsonPath(
                        "$.restaurants[1].dailyMenus[0].menuDate"
                ).value("2026-08-17"))
                .andExpect(jsonPath(
                        "$.restaurants[1].dailyMenus[1].menuDate"
                ).value("2026-08-21"));
    }

    @Test
    void returnsTwoEmptyBucketsInsteadOfNotFound() throws Exception {
        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("menuDate", "2000-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value(nullValue()))
                .andExpect(jsonPath("$.restaurants.length()").value(2))
                .andExpect(jsonPath("$.restaurants[0].restaurantType")
                        .value("MAIN_STUDENT"))
                .andExpect(jsonPath("$.restaurants[0].dailyMenus").isEmpty())
                .andExpect(jsonPath("$.restaurants[1].restaurantType")
                        .value("TAEAN_STUDENT"))
                .andExpect(jsonPath("$.restaurants[1].dailyMenus").isEmpty());
    }

    @Test
    void rejectsInvalidDate() throws Exception {
        mockMvc.perform(get("/api/cafeteria/menus")
                        .param("menuDate", "2026/08/20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesWrapperContractWithoutRemovedParametersInOpenApi()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.parameters[*].name"
                ).value(hasItem("menuDate")))
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.parameters"
                                + "[?(@.name == 'menuCategory')]"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.parameters"
                                + "[?(@.name == 'restaurantType')]"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.responses['200']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.responses['200']"
                                + ".content['application/json'].schema['$ref']"
                ).value("#/components/schemas/CafeteriaMenusResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.responses['400']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/cafeteria/menus'].get.responses['401']"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.CafeteriaMenusResponse.properties"
                                + ".preferredRestaurantType"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.CafeteriaMenusResponse.properties"
                                + ".restaurants.items['$ref']"
                ).value(
                        "#/components/schemas/"
                                + "CafeteriaRestaurantMenusResponse"
                ))
                .andExpect(jsonPath(
                        "$.components.schemas.CafeteriaRestaurantMenusResponse"
                                + ".properties.dailyMenus.items['$ref']"
                ).value("#/components/schemas/DailyMenuDTO"))
                .andExpect(jsonPath(
                        "$.components.schemas.MealSectionDTO.properties.cornerName"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.MealSectionDTO.properties.rawText"
                ).exists());
    }

    private record SectionFixture(
            MealTime mealTime,
            String cornerName,
            Integer price,
            List<String> dishes,
            String rawText
    ) {
    }

    private void saveDailyMenu(
            String restaurantType,
            LocalDate menuDate,
            SectionFixture... sections
    ) {
        DailyMenu dailyMenu = DailyMenu.of(
                RestaurantType.valueOf(restaurantType), menuDate);
        for (SectionFixture section : sections) {
            dailyMenu.addMealSection(
                    section.mealTime(),
                    section.cornerName(),
                    section.price(),
                    section.dishes(),
                    section.rawText()
            );
        }
        dailyMenuRepository.saveAndFlush(dailyMenu);
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE meal_sections");
            jdbcTemplate.execute("TRUNCATE TABLE daily_menus");
            jdbcTemplate.execute("TRUNCATE TABLE user_accounts");
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
