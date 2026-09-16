package hsu.hanseomate.domain.home.dto;

import hsu.hanseomate.domain.cafeteria.entity.MealSection;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import java.util.List;

public record HomeCafeteriaMealSectionResponse(
        MealTime mealTime,
        String cornerName,
        Integer price,
        List<String> dishes,
        String rawText
) {

    public HomeCafeteriaMealSectionResponse {
        dishes = List.copyOf(dishes);
    }

    public static HomeCafeteriaMealSectionResponse from(MealSection section) {
        return new HomeCafeteriaMealSectionResponse(
                section.getMealTime(),
                section.getCornerName(),
                section.getPrice(),
                List.copyOf(section.getDishes()),
                section.getRawText()
        );
    }
}
