package hsu.hanseomate.domain.cafeteria.dto;

import hsu.hanseomate.domain.cafeteria.entity.MealSection;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import java.util.List;

public record MealSectionDTO(
        Long id,
        MealTime mealTime,
        String cornerName,
        Integer price,
        List<String> dishes,
        String rawText
) {

    public static MealSectionDTO from(MealSection section) {
        return new MealSectionDTO(
                section.getId(),
                section.getMealTime(),
                section.getCornerName(),
                section.getPrice(),
                List.copyOf(section.getDishes()),
                section.getRawText()
        );
    }
}
