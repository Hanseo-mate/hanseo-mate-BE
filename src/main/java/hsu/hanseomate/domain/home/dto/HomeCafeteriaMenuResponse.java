package hsu.hanseomate.domain.home.dto;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MealSection;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record HomeCafeteriaMenuResponse(
        @Schema(allowableValues = {"MAIN_STUDENT", "TAEAN_STUDENT"})
        RestaurantType restaurantType,
        LocalDate menuDate,
        List<HomeCafeteriaMealSectionResponse> mealSections
) {

    public HomeCafeteriaMenuResponse {
        mealSections = List.copyOf(mealSections);
    }

    public static HomeCafeteriaMenuResponse from(DailyMenu dailyMenu) {
        List<HomeCafeteriaMealSectionResponse> sections = dailyMenu
                .getMealSections()
                .stream()
                .sorted(Comparator
                        .comparingInt((MealSection section) ->
                                section.getMealTime().ordinal())
                        .thenComparingInt(section ->
                                section.getMenuCategory().ordinal())
                        .thenComparing(
                                MealSection::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                .map(HomeCafeteriaMealSectionResponse::from)
                .toList();
        return new HomeCafeteriaMenuResponse(
                dailyMenu.getRestaurantType(),
                dailyMenu.getMenuDate(),
                sections
        );
    }
}
