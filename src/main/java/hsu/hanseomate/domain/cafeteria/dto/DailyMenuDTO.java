package hsu.hanseomate.domain.cafeteria.dto;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.LocalDate;
import java.util.List;

public record DailyMenuDTO(
        Long id,
        RestaurantType restaurantType,
        LocalDate menuDate,
        List<MealSectionDTO> mealSections
) {

    public static DailyMenuDTO from(DailyMenu dailyMenu) {
        List<MealSectionDTO> sectionDTOs = dailyMenu.getMealSections().stream()
                .map(MealSectionDTO::from)
                .toList();
        return new DailyMenuDTO(
                dailyMenu.getId(),
                dailyMenu.getRestaurantType(),
                dailyMenu.getMenuDate(),
                sectionDTOs
        );
    }
}
