package hsu.hanseomate.domain.cafeteria.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(
        name = "daily_menus",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_daily_menu_restaurant_date",
                columnNames = {"restaurant_type", "menu_date"}
        )
)
public class DailyMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "restaurant_type", nullable = false, length = 20)
    private RestaurantType restaurantType;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @BatchSize(size = 30)
    @OneToMany(mappedBy = "dailyMenu", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<MealSection> mealSections = new ArrayList<>();

    protected DailyMenu() {
    }

    public static DailyMenu of(RestaurantType restaurantType, LocalDate menuDate) {
        DailyMenu dailyMenu = new DailyMenu();
        dailyMenu.restaurantType = restaurantType;
        dailyMenu.menuDate = menuDate;
        return dailyMenu;
    }

    /**
     * 새 MealSection 을 생성해 이 식단에 추가한다. 리스트 추가 순서(= id 증가 순서)가
     * 크롤러가 전달한 원본 코너 순서를 그대로 보존한다.
     */
    public MealSection addMealSection(
            MealTime mealTime,
            String cornerName,
            Integer price,
            List<String> dishes,
            String rawText
    ) {
        MealSection section = MealSection.create(
                this, mealTime, cornerName, price, dishes, rawText
        );
        mealSections.add(section);
        return section;
    }

    public Long getId() {
        return id;
    }

    public RestaurantType getRestaurantType() {
        return restaurantType;
    }

    public LocalDate getMenuDate() {
        return menuDate;
    }

    public List<MealSection> getMealSections() {
        return Collections.unmodifiableList(mealSections);
    }
}
