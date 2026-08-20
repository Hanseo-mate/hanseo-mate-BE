package hsu.hanseomate.domain.cafeteria.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import hsu.hanseomate.domain.cafeteria.entity.QDailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.QMealSection;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.LocalDate;
import java.util.Collection;
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
            Collection<RestaurantType> restaurantTypes,
            LocalDate startDate,
            LocalDate endDate,
            MenuCategory menuCategory
    ) {
        QDailyMenu dailyMenu = QDailyMenu.dailyMenu;
        QMealSection mealSection = QMealSection.mealSection;

        // MealSection 필터를 유지하면서 상위 엔티티를 두 단계로 조회한다:
        //   1단계: DailyMenu ID 목록 조회 (조건 적용)
        //   2단계: 해당 ID 의 DailyMenu → MealSection 을 fetch join
        // Dish 는 엔티티의 @BatchSize 로 일괄 조회한다.
        // MySQL은 SELECT DISTINCT id에서 선택하지 않은 menu_date 정렬을
        // 허용하지 않으므로 최종 정렬은 2단계 조회에서만 수행한다.
        List<Long> dailyMenuIds = queryFactory
                .select(dailyMenu.id)
                .from(dailyMenu)
                .join(dailyMenu.mealSections, mealSection)
                .where(
                        dailyMenu.restaurantType.in(restaurantTypes),
                        menuDateRange(dailyMenu, startDate, endDate),
                        menuCategoryEq(mealSection, menuCategory)
                )
                .distinct()
                .fetch();

        if (dailyMenuIds.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .selectFrom(dailyMenu)
                .join(dailyMenu.mealSections, mealSection).fetchJoin()
                .where(
                        dailyMenu.id.in(dailyMenuIds),
                        menuCategoryEq(mealSection, menuCategory)
                )
                .distinct()
                .orderBy(
                        dailyMenu.restaurantType.asc(),
                        dailyMenu.menuDate.asc(),
                        mealSection.mealTime.asc(),
                        mealSection.menuCategory.asc()
                )
                .fetch();
    }

    // ─── 조건 헬퍼 ────────────────────────────────────────────────────────────

    private BooleanExpression menuDateRange(
            QDailyMenu dailyMenu,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return dailyMenu.menuDate.between(startDate, endDate);
    }

    private BooleanExpression menuCategoryEq(QMealSection mealSection, MenuCategory menuCategory) {
        return menuCategory != null ? mealSection.menuCategory.eq(menuCategory) : null;
    }
}
