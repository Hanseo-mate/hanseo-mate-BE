package hsu.hanseomate.domain.cafeteria.exception;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.LocalDate;

public class CafeteriaMenuNotFoundException extends RuntimeException {

    public CafeteriaMenuNotFoundException(
            RestaurantType restaurantType,
            LocalDate menuDate
    ) {
        super(String.format(
                "식단 데이터를 찾을 수 없습니다. [restaurantType=%s, menuDate=%s]",
                restaurantType,
                menuDate != null ? menuDate : "이번 주 전체"
        ));
    }
}
