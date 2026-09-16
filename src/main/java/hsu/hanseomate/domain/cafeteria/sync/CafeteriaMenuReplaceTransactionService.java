package hsu.hanseomate.domain.cafeteria.sync;

import hsu.hanseomate.domain.cafeteria.client.CafeteriaDailyMenuCrawlDto;
import hsu.hanseomate.domain.cafeteria.client.CafeteriaMealSectionCrawlDto;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.repository.DailyMenuRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 변경이 감지된 한 식당의 주간 식단을 하나의 트랜잭션에서 전량 삭제 후 재삽입한다.
 * <p>
 * 오케스트레이터(비트랜잭셔널)에서 별도 Spring 빈으로 주입해 호출하므로 self-invocation
 * 으로 인한 {@code @Transactional} 미적용 문제가 없다. 삭제·삽입 중 하나라도 실패하면
 * 트랜잭션 전체가 롤백된다(부분 반영 없음).
 */
@Service
public class CafeteriaMenuReplaceTransactionService {

    private static final Logger log =
            LoggerFactory.getLogger(CafeteriaMenuReplaceTransactionService.class);

    private final DailyMenuRepository dailyMenuRepository;

    public CafeteriaMenuReplaceTransactionService(DailyMenuRepository dailyMenuRepository) {
        this.dailyMenuRepository = dailyMenuRepository;
    }

    /**
     * 해당 restaurantType 의 기존 DailyMenu(+cascade MealSection)를 모두 삭제하고
     * newMenus 를 삽입한다. 다른 restaurantType 의 데이터는 절대 건드리지 않는다.
     *
     * @param restaurantType 대상 식당
     * @param newMenus       삽입할 크롤 결과
     */
    @Transactional
    public void replaceWeek(
            RestaurantType restaurantType,
            List<CafeteriaDailyMenuCrawlDto> newMenus
    ) {
        List<DailyMenu> existing = dailyMenuRepository
                .findAllByRestaurantTypeOrderByMenuDateAscIdAsc(restaurantType);
        dailyMenuRepository.deleteAll(existing);
        // (restaurant_type, menu_date) unique 제약과 충돌하지 않도록 삽입 전에 삭제를 flush.
        dailyMenuRepository.flush();

        for (CafeteriaDailyMenuCrawlDto dayDto : newMenus) {
            DailyMenu dailyMenu = DailyMenu.of(restaurantType, dayDto.menuDate());
            List<CafeteriaMealSectionCrawlDto> sections = dayDto.mealSections() == null
                    ? List.of() : dayDto.mealSections();
            for (CafeteriaMealSectionCrawlDto sectionDto : sections) {
                dailyMenu.addMealSection(
                        sectionDto.mealTime(),
                        sectionDto.cornerName(),
                        sectionDto.price(),
                        sectionDto.dishes() == null ? List.of() : sectionDto.dishes(),
                        sectionDto.rawText()
                );
            }
            dailyMenuRepository.save(dailyMenu);
        }
        dailyMenuRepository.flush();

        log.info(
                "[CafeteriaReplace] Replaced week: restaurantType={}, removed={}, inserted={}",
                restaurantType, existing.size(), newMenus.size()
        );
    }
}
