package hsu.hanseomate.domain.cafeteria.dto;

import hsu.hanseomate.domain.cafeteria.entity.MealSection;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import java.util.List;

public record MealSectionDTO(
        Long id,
        MealTime mealTime,
        MenuCategory menuCategory,
        List<DishDTO> dishes
) {

    public static MealSectionDTO from(MealSection section) {
        List<DishDTO> dishDTOs = section.getDishes().stream()
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
