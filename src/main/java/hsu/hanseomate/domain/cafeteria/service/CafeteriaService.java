package hsu.hanseomate.domain.cafeteria.service;

import hsu.hanseomate.domain.cafeteria.dto.DailyMenuDTO;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.exception.CafeteriaMenuNotFoundException;
import hsu.hanseomate.domain.cafeteria.repository.DailyMenuRepository;
import hsu.hanseomate.global.exception.BadRequestException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CafeteriaService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyMenuRepository dailyMenuRepository;
    private final Clock clock;

    public CafeteriaService(
            DailyMenuRepository dailyMenuRepository,
            Clock clock
    ) {
        this.dailyMenuRepository = dailyMenuRepository;
        this.clock = clock;
    }

    /**
     * 조건에 맞는 식단 목록을 조회하여 DTO 리스트로 반환한다.
     *
     * @param restaurantType 필수 — 서산 또는 태안 학생식당
     * @param menuDate       선택 — null 이면 한국 기준 이번 주 월~금 조회
     * @param menuCategory   선택 — null 이면 모든 카테고리 반환
     * @return 계층형 DailyMenuDTO 리스트 (비어 있으면 404 예외)
     */
    public List<DailyMenuDTO> getMenus(
            RestaurantType restaurantType,
            LocalDate menuDate,
            MenuCategory menuCategory
    ) {
        validateStudentRestaurant(restaurantType);
        LocalDate startDate = menuDate;
        LocalDate endDate = menuDate;
        if (menuDate == null) {
            LocalDate today = LocalDate.now(clock.withZone(KOREA_ZONE));
            startDate = today.with(DayOfWeek.MONDAY);
            endDate = startDate.plusDays(4);
        }

        List<DailyMenu> dailyMenus = dailyMenuRepository.findMenus(
                restaurantType,
                startDate,
                endDate,
                menuCategory
        );

        if (dailyMenus.isEmpty()) {
            throw new CafeteriaMenuNotFoundException(restaurantType, menuDate);
        }

        return dailyMenus.stream()
                .map(DailyMenuDTO::from)
                .toList();
    }

    private void validateStudentRestaurant(RestaurantType restaurantType) {
        if (restaurantType != RestaurantType.MAIN_STUDENT
                && restaurantType != RestaurantType.TAEAN_STUDENT) {
            throw new BadRequestException(
                    "restaurantType은 MAIN_STUDENT 또는 TAEAN_STUDENT만 사용할 수 있습니다."
            );
        }
    }
}
