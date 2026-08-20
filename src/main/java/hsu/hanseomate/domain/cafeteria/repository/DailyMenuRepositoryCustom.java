package hsu.hanseomate.domain.cafeteria.repository;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.LocalDate;
import java.util.List;

public interface DailyMenuRepositoryCustom {

    /**
     * restaurantType 은 필수.
     * startDate 부터 endDate 까지 해당 식당의 식단을 반환.
     * menuCategory 가 null 이면 모든 카테고리의 MealSection 을 반환.
     */
    List<DailyMenu> findMenus(
            RestaurantType restaurantType,
            LocalDate startDate,
            LocalDate endDate,
            MenuCategory menuCategory
    );
}
