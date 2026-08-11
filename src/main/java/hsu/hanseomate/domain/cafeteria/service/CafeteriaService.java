package hsu.hanseomate.domain.cafeteria.service;

import hsu.hanseomate.domain.cafeteria.dto.DailyMenuDTO;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.exception.CafeteriaMenuNotFoundException;
import hsu.hanseomate.domain.cafeteria.repository.DailyMenuRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CafeteriaService {

    private final DailyMenuRepository dailyMenuRepository;

    public CafeteriaService(DailyMenuRepository dailyMenuRepository) {
        this.dailyMenuRepository = dailyMenuRepository;
    }

    /**
     * 조건에 맞는 식단 목록을 조회하여 DTO 리스트로 반환한다.
     *
     * @param restaurantType 필수 — 식당 종류
     * @param menuDate       선택 — null 이면 이번 주 전체 조회
     * @param menuCategory   선택 — null 이면 모든 카테고리 반환
     * @return 계층형 DailyMenuDTO 리스트 (비어 있으면 404 예외)
     */
    public List<DailyMenuDTO> getMenus(
            RestaurantType restaurantType,
            LocalDate menuDate,
            MenuCategory menuCategory
    ) {
        List<DailyMenu> dailyMenus = dailyMenuRepository.findMenus(restaurantType, menuDate, menuCategory);

        if (dailyMenus.isEmpty()) {
            throw new CafeteriaMenuNotFoundException(restaurantType, menuDate);
        }

        return dailyMenus.stream()
                .map(DailyMenuDTO::from)
                .toList();
    }
}
