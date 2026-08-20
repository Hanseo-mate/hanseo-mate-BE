package hsu.hanseomate.domain.cafeteria.repository;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyMenuRepository extends JpaRepository<DailyMenu, Long>, DailyMenuRepositoryCustom {

    @EntityGraph(attributePaths = "mealSections")
    List<DailyMenu> findAllByMenuDateAndRestaurantTypeInOrderByIdAsc(
            LocalDate menuDate,
            Collection<RestaurantType> restaurantTypes
    );
}
