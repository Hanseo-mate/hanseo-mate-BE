package hsu.hanseomate.domain.cafeteria.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hsu.hanseomate.domain.cafeteria.client.CafeteriaDailyMenuCrawlDto;
import hsu.hanseomate.domain.cafeteria.client.CafeteriaMealSectionCrawlDto;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.repository.DailyMenuRepository;
import hsu.hanseomate.global.config.QueryDslConfig;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CafeteriaMenuReplaceTransactionService} 의 실제 트랜잭션 동작 테스트.
 * <p>
 * 테스트 메서드에 {@code @Transactional(NOT_SUPPORTED)} 를 걸어 주변 테스트 트랜잭션을
 * 제거함으로써, 서비스의 {@code @Transactional} 이 실제로 커밋/롤백하도록 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({QueryDslConfig.class, CafeteriaMenuReplaceTransactionService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CafeteriaMenuReplaceTransactionServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 31);

    @Autowired
    private CafeteriaMenuReplaceTransactionService replaceService;

    @Autowired
    private DailyMenuRepository dailyMenuRepository;

    @BeforeEach
    void clean() {
        dailyMenuRepository.deleteAllInBatch();
    }

    @AfterEach
    void cleanAfter() {
        dailyMenuRepository.deleteAllInBatch();
    }

    private void seed(RestaurantType type, String dish) {
        DailyMenu menu = DailyMenu.of(type, DATE);
        menu.addMealSection(MealTime.LUNCH, "코너", 5000, List.of(dish), "raw");
        dailyMenuRepository.saveAndFlush(menu);
    }

    private CafeteriaDailyMenuCrawlDto day(String dish, String rawText) {
        return new CafeteriaDailyMenuCrawlDto(DATE, RestaurantType.MAIN_STUDENT,
                List.of(new CafeteriaMealSectionCrawlDto(
                        MealTime.LUNCH, "코너", 6000, List.of(dish), rawText)));
    }

    @Test
    void changedData_replacesTargetRestaurantAndLeavesOthersUntouched() {
        seed(RestaurantType.MAIN_STUDENT, "옛날 메뉴");
        seed(RestaurantType.TAEAN_STUDENT, "태안 메뉴");

        replaceService.replaceWeek(
                RestaurantType.MAIN_STUDENT, List.of(day("새 메뉴", "raw")));

        List<DailyMenu> main = dailyMenuRepository
                .findAllByRestaurantTypeOrderByMenuDateAscIdAsc(RestaurantType.MAIN_STUDENT);
        assertThat(main).hasSize(1);
        assertThat(main.get(0).getMealSections().get(0).getDishes())
                .containsExactly("새 메뉴");

        List<DailyMenu> taean = dailyMenuRepository
                .findAllByRestaurantTypeOrderByMenuDateAscIdAsc(RestaurantType.TAEAN_STUDENT);
        assertThat(taean).hasSize(1);
        assertThat(taean.get(0).getMealSections().get(0).getDishes())
                .containsExactly("태안 메뉴");
    }

    @Test
    void insertFailureMidTransaction_rollsBackDeletesAndInserts() {
        seed(RestaurantType.MAIN_STUDENT, "기존 메뉴");

        // 두 번째 날의 rawText 가 null → NOT NULL 제약 위반으로 삽입 중 실패.
        List<CafeteriaDailyMenuCrawlDto> newMenus = Arrays.asList(
                new CafeteriaDailyMenuCrawlDto(DATE, RestaurantType.MAIN_STUDENT,
                        List.of(new CafeteriaMealSectionCrawlDto(
                                MealTime.LUNCH, "코너", 6000, List.of("정상"), "raw"))),
                new CafeteriaDailyMenuCrawlDto(DATE.plusDays(1), RestaurantType.MAIN_STUDENT,
                        List.of(new CafeteriaMealSectionCrawlDto(
                                MealTime.LUNCH, "코너", 6000, List.of("불량"), null)))
        );

        assertThatThrownBy(() ->
                replaceService.replaceWeek(RestaurantType.MAIN_STUDENT, newMenus))
                .isInstanceOf(Exception.class);

        // 삭제와 삽입이 모두 롤백되어 기존 데이터가 그대로 남아 있어야 한다.
        List<DailyMenu> main = dailyMenuRepository
                .findAllByRestaurantTypeOrderByMenuDateAscIdAsc(RestaurantType.MAIN_STUDENT);
        assertThat(main).hasSize(1);
        assertThat(main.get(0).getMealSections().get(0).getDishes())
                .containsExactly("기존 메뉴");
    }

    @Test
    void emptyMenusList_deletesTargetRestaurant() {
        seed(RestaurantType.MAIN_STUDENT, "기존 메뉴");

        replaceService.replaceWeek(RestaurantType.MAIN_STUDENT, List.of());

        assertThat(dailyMenuRepository
                .findAllByRestaurantTypeOrderByMenuDateAscIdAsc(RestaurantType.MAIN_STUDENT))
                .isEmpty();
    }
}
