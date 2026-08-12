package hsu.hanseomate.domain.cafeteria.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "meal_sections")
public class MealSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_menu_id", nullable = false)
    private DailyMenu dailyMenu;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_time", nullable = false, length = 10)
    private MealTime mealTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "menu_category", nullable = false, length = 10)
    private MenuCategory menuCategory;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "mealSection", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<Dish> dishes = new ArrayList<>();

    protected MealSection() {
    }

    public static MealSection of(DailyMenu dailyMenu, MealTime mealTime, MenuCategory menuCategory) {
        MealSection section = new MealSection();
        section.dailyMenu = dailyMenu;
        section.mealTime = mealTime;
        section.menuCategory = menuCategory;
        return section;
    }

    public Long getId() {
        return id;
    }

    public DailyMenu getDailyMenu() {
        return dailyMenu;
    }

    public MealTime getMealTime() {
        return mealTime;
    }

    public MenuCategory getMenuCategory() {
        return menuCategory;
    }

    public List<Dish> getDishes() {
        return Collections.unmodifiableList(dishes);
    }
}
