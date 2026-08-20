package hsu.hanseomate.domain.cafeteria.dto;

import hsu.hanseomate.domain.cafeteria.entity.Dish;
import hsu.hanseomate.domain.cafeteria.entity.MealSection;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import java.util.Comparator;
import java.util.List;

public record MealSectionDTO(
        Long id,
        MealTime mealTime,
        MenuCategory menuCategory,
        List<DishDTO> dishes
) {

    public static MealSectionDTO from(MealSection section) {
        List<DishDTO> dishDTOs = section.getDishes().stream()
                .sorted(Comparator.comparing(
                        Dish::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .map(DishDTO::from)
                .toList();
        return new MealSectionDTO(
                section.getId(),
                section.getMealTime(),
                section.getMenuCategory(),
                dishDTOs
        );
    }
}
