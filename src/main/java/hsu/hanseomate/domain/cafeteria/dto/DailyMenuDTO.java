package hsu.hanseomate.domain.cafeteria.dto;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MealSection;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record DailyMenuDTO(
        Long id,
        @Schema(allowableValues = {"MAIN_STUDENT", "TAEAN_STUDENT"})
        RestaurantType restaurantType,
        LocalDate menuDate,
        DayOfWeek dayOfWeek,
        List<MealSectionDTO> mealSections
) {

    public static DailyMenuDTO from(DailyMenu dailyMenu) {
        List<MealSectionDTO> sectionDTOs = dailyMenu.getMealSections().stream()
                .sorted(Comparator
                        .comparingInt((MealSection section) ->
                                section.getMealTime().ordinal())
                        .thenComparing(
                                MealSection::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .map(MealSectionDTO::from)
                .toList();
        return new DailyMenuDTO(
                dailyMenu.getId(),
                dailyMenu.getRestaurantType(),
                dailyMenu.getMenuDate(),
                dailyMenu.getMenuDate().getDayOfWeek(),
                sectionDTOs
        );
    }
}
