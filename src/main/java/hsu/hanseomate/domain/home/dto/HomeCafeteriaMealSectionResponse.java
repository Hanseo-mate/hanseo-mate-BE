package hsu.hanseomate.domain.home.dto;

import hsu.hanseomate.domain.cafeteria.entity.Dish;
import hsu.hanseomate.domain.cafeteria.entity.MealSection;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import java.util.Comparator;
import java.util.List;

public record HomeCafeteriaMealSectionResponse(
        MealTime mealTime,
        MenuCategory menuCategory,
        List<HomeCafeteriaDishResponse> dishes
) {

    public HomeCafeteriaMealSectionResponse {
        dishes = List.copyOf(dishes);
    }

    public static HomeCafeteriaMealSectionResponse from(MealSection section) {
        List<HomeCafeteriaDishResponse> dishes = section.getDishes().stream()
                .sorted(Comparator.comparing(
                        Dish::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .map(HomeCafeteriaDishResponse::from)
                .toList();
        return new HomeCafeteriaMealSectionResponse(
                section.getMealTime(),
                section.getMenuCategory(),
                dishes
        );
    }
}
