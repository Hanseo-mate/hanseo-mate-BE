package hsu.hanseomate.domain.cafeteria.repository;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyMenuRepository extends JpaRepository<DailyMenu, Integer>, DailyMenuRepositoryCustom {

    @EntityGraph(attributePaths = "mealSections")
    List<DailyMenu> findAllByMenuDateAndRestaurantTypeInOrderByIdAsc(
            LocalDate menuDate,
            Collection<RestaurantType> restaurantTypes
    );

    /**
     * 한 식당의 모든 식단을 MealSection 과 함께(fetch join) 조회한다.
     * 주간 비교(replace-if-changed) 시 DB 스냅샷 로딩에 사용하며 N+1 을 방지한다.
     */
    @EntityGraph(attributePaths = "mealSections")
    List<DailyMenu> findAllByRestaurantTypeOrderByMenuDateAscIdAsc(
            RestaurantType restaurantType
    );

    /**
     * 한 식당의 모든 식단을 삭제한다.
     * 파생 delete 이므로 엔티티를 로딩한 뒤 개별 삭제하여 cascade/orphanRemoval
     * (MealSection 삭제)이 적용된다.
     */
    long deleteByRestaurantType(RestaurantType restaurantType);
}
