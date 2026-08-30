package hsu.hanseomate.domain.cafeteria.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
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
            LocalDate endDate
    ) {
        QDailyMenu dailyMenu = QDailyMenu.dailyMenu;
        QMealSection mealSection = QMealSection.mealSection;

        // 상위 엔티티를 두 단계로 조회한다:
        //   1단계: 조건에 맞는 DailyMenu ID 목록 조회
        //   2단계: 해당 ID 의 DailyMenu → MealSection 을 fetch join (N+1 방지)
        List<Long> dailyMenuIds = queryFactory
                .select(dailyMenu.id)
                .from(dailyMenu)
                .where(
                        dailyMenu.restaurantType.in(restaurantTypes),
                        menuDateRange(dailyMenu, startDate, endDate)
                )
                .distinct()
                .fetch();

        if (dailyMenuIds.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .selectFrom(dailyMenu)
                .leftJoin(dailyMenu.mealSections, mealSection).fetchJoin()
                .where(dailyMenu.id.in(dailyMenuIds))
                .distinct()
                .orderBy(
                        dailyMenu.restaurantType.asc(),
                        dailyMenu.menuDate.asc(),
                        mealSection.mealTime.asc(),
                        mealSection.id.asc()
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
}
