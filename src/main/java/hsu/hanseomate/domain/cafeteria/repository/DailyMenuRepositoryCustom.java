package hsu.hanseomate.domain.cafeteria.repository;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DailyMenuRepositoryCustom {

    /**
     * restaurantTypes 에 포함된 식당의 startDate~endDate 식단을 MealSection 과 함께 반환한다.
     */
    List<DailyMenu> findMenus(
            Collection<RestaurantType> restaurantTypes,
            LocalDate startDate,
            LocalDate endDate
    );
}
