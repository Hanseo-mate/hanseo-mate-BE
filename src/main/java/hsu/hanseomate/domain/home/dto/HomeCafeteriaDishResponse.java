package hsu.hanseomate.domain.home.dto;

import hsu.hanseomate.domain.cafeteria.entity.Dish;

public record HomeCafeteriaDishResponse(
        String name,
        boolean isMainDish
) {

    public static HomeCafeteriaDishResponse from(Dish dish) {
        return new HomeCafeteriaDishResponse(
                dish.getName(),
                Boolean.TRUE.equals(dish.getIsMainDish())
        );
    }
}
