package hsu.hanseomate.domain.cafeteria.repository;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MealSection;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.global.config.QueryDslConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(QueryDslConfig.class)
class DailyMenuRepositoryTest {

    @Autowired
    private DailyMenuRepository dailyMenuRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsDishesJsonListPreservingOrder() {
        DailyMenu menu = DailyMenu.of(RestaurantType.MAIN_STUDENT, LocalDate.of(2026, 8, 31));
        menu.addMealSection(
                MealTime.LUNCH,
                "1코너",
                5500,
                List.of("쌀밥", "불고기", "김치"),
                "1코너 (5.5)\n쌀밥\n불고기\n김치"
        );
        DailyMenu saved = dailyMenuRepository.saveAndFlush(menu);
        entityManager.clear();

        DailyMenu reloaded = dailyMenuRepository.findById(saved.getId()).orElseThrow();
        MealSection section = reloaded.getMealSections().get(0);
        assertThat(section.getDishes())
                .containsExactly("쌀밥", "불고기", "김치");
        assertThat(section.getCornerName()).isEqualTo("1코너");
        assertThat(section.getPrice()).isEqualTo(5500);
    }

    @Test
    void persistsNullPriceAndMultilineKoreanRawText() {
        DailyMenu menu = DailyMenu.of(RestaurantType.TAEAN_STUDENT, LocalDate.of(2026, 8, 31));
        String rawText = "코너\n한글 여러 줄\n텍스트";
        menu.addMealSection(MealTime.DINNER, "가정식", null, List.of("된장국"), rawText);
        DailyMenu saved = dailyMenuRepository.saveAndFlush(menu);
        entityManager.clear();

        MealSection section = dailyMenuRepository.findById(saved.getId())
                .orElseThrow().getMealSections().get(0);
        assertThat(section.getPrice()).isNull();
        assertThat(section.getRawText()).isEqualTo(rawText);
        assertThat(section.getDishes()).containsExactly("된장국");
    }

    @Test
    void fetchingManyDailyMenusWithSectionsDoesNotTriggerNPlusOne() {
        for (int offset = 0; offset < 5; offset++) {
            DailyMenu menu = DailyMenu.of(
                    RestaurantType.MAIN_STUDENT, LocalDate.of(2026, 8, 17).plusDays(offset));
            menu.addMealSection(MealTime.LUNCH, "코너", 5000, List.of("메뉴" + offset), "raw");
            menu.addMealSection(MealTime.DINNER, "코너", 6000, List.of("저녁" + offset), "raw");
            dailyMenuRepository.saveAndFlush(menu);
        }
        entityManager.clear();

        SessionFactory sessionFactory = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<DailyMenu> menus = dailyMenuRepository.findMenus(
                List.of(RestaurantType.MAIN_STUDENT),
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 21)
        );
        int touched = menus.stream()
                .mapToInt(menu -> menu.getMealSections().size())
                .sum();

        assertThat(menus).hasSize(5);
        assertThat(touched).isEqualTo(10);
        // ID 조회 1 + fetch-join 1 = 2 쿼리. N+1 이면 5개 daily menu 당 추가 쿼리가 발생한다.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void deleteByRestaurantTypeRemovesOnlyThatRestaurant() {
        DailyMenu main = DailyMenu.of(RestaurantType.MAIN_STUDENT, LocalDate.of(2026, 8, 31));
        main.addMealSection(MealTime.LUNCH, "코너", 5000, List.of("A"), "raw");
        dailyMenuRepository.saveAndFlush(main);

        DailyMenu taean = DailyMenu.of(RestaurantType.TAEAN_STUDENT, LocalDate.of(2026, 8, 31));
        taean.addMealSection(MealTime.LUNCH, "코너", 5000, List.of("B"), "raw");
        dailyMenuRepository.saveAndFlush(taean);
        entityManager.clear();

        dailyMenuRepository.deleteByRestaurantType(RestaurantType.MAIN_STUDENT);
        dailyMenuRepository.flush();
        entityManager.clear();

        assertThat(dailyMenuRepository.findAllByRestaurantTypeOrderByMenuDateAscIdAsc(
                RestaurantType.MAIN_STUDENT)).isEmpty();
        assertThat(dailyMenuRepository.findAllByRestaurantTypeOrderByMenuDateAscIdAsc(
                RestaurantType.TAEAN_STUDENT)).hasSize(1);
    }
}
