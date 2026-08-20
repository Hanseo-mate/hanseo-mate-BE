package hsu.hanseomate.domain.cafeteria.repository;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DailyMenuRepositoryCustom {

    /**
     * restaurantTypes 에 포함된 식당의 startDate~endDate 식단을 반환.
     * menuCategory 가 null 이면 모든 카테고리의 MealSection 을 반환.
     */
    List<DailyMenu> findMenus(
            Collection<RestaurantType> restaurantTypes,
            LocalDate startDate,
            LocalDate endDate,
            MenuCategory menuCategory
    );
}
