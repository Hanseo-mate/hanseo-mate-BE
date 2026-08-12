package hsu.hanseomate.domain.cafeteria.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import hsu.hanseomate.domain.cafeteria.entity.QDailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.QDish;
import hsu.hanseomate.domain.cafeteria.entity.QMealSection;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class DailyMenuRepositoryImpl implements DailyMenuRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public DailyMenuRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<DailyMenu> findMenus(
            RestaurantType restaurantType,
            LocalDate menuDate,
            MenuCategory menuCategory
    ) {
        QDailyMenu dailyMenu = QDailyMenu.dailyMenu;
        QMealSection mealSection = QMealSection.mealSection;
        QDish dish = QDish.dish;

        // MealSection 필터(카테고리)를 포함한 Dish 까지 한 번에 fetch join.
        // N+1 을 막기 위해 두 단계로 쪼개어 조회한다:
        //   1단계: DailyMenu ID 목록 조회 (조건 적용)
        //   2단계: 해당 ID 의 DailyMenu → MealSection → Dish 를 fetch join
        List<Long> dailyMenuIds = queryFactory
                .select(dailyMenu.id)
                .from(dailyMenu)
                .join(dailyMenu.mealSections, mealSection)
                .where(
                        restaurantTypeEq(dailyMenu, restaurantType),
                        menuDateCondition(dailyMenu, menuDate),
                        menuCategoryEq(mealSection, menuCategory)
                )
                .distinct()
                .orderBy(dailyMenu.menuDate.asc())
                .fetch();

        if (dailyMenuIds.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .selectFrom(dailyMenu)
                .join(dailyMenu.mealSections, mealSection).fetchJoin()
                .join(mealSection.dishes, dish).fetchJoin()
                .where(
                        dailyMenu.id.in(dailyMenuIds),
                        menuCategoryEq(mealSection, menuCategory)
                )
                .distinct()
                .orderBy(dailyMenu.menuDate.asc(), mealSection.mealTime.asc(), mealSection.menuCategory.asc())
                .fetch();
    }

    // ─── 조건 헬퍼 ────────────────────────────────────────────────────────────

    private BooleanExpression restaurantTypeEq(QDailyMenu dailyMenu, RestaurantType restaurantType) {
        return dailyMenu.restaurantType.eq(restaurantType);
    }

    /**
     * menuDate 가 주어지면 해당 날짜만, 없으면 해당 날짜가 속한 주(월~일) 전체를 조회한다.
     * menuDate 가 null 이면 오늘 기준 이번 주를 반환한다.
     */
    private BooleanExpression menuDateCondition(QDailyMenu dailyMenu, LocalDate menuDate) {
        if (menuDate != null) {
            return dailyMenu.menuDate.eq(menuDate);
        }
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);
        return dailyMenu.menuDate.between(weekStart, weekEnd);
    }

    private BooleanExpression menuCategoryEq(QMealSection mealSection, MenuCategory menuCategory) {
        return menuCategory != null ? mealSection.menuCategory.eq(menuCategory) : null;
    }
}
