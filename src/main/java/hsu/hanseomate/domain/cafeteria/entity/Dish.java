package hsu.hanseomate.domain.cafeteria.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "dishes")
public class Dish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_section_id", nullable = false)
    private MealSection mealSection;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "is_main_dish", nullable = false)
    private Boolean isMainDish;

    protected Dish() {
    }

    public static Dish of(MealSection mealSection, String name, Boolean isMainDish) {
        Dish dish = new Dish();
        dish.mealSection = mealSection;
        dish.name = name;
        dish.isMainDish = isMainDish;
        return dish;
    }

    public Long getId() {
        return id;
    }

    public MealSection getMealSection() {
        return mealSection;
    }

    public String getName() {
        return name;
    }

    public Boolean getIsMainDish() {
        return isMainDish;
    }
}
