package hsu.hanseomate.domain.cafeteria.dto;

import hsu.hanseomate.domain.cafeteria.entity.Dish;

public record DishDTO(
        Long id,
        String name,
        Boolean isMainDish
) {

    public static DishDTO from(Dish dish) {
        return new DishDTO(dish.getId(), dish.getName(), dish.getIsMainDish());
    }
}
