package hsu.hanseomate.domain.cafeteria.client;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;

/**
 * Python 크롤러 호출 실패(timeout/4xx/5xx/역직렬화 실패 등)를 감싸는 도메인 예외.
 * 실패한 식당 유형을 포함하여 구조적 로깅과 재시도 카운팅에 사용한다.
 */
public class CafeteriaCrawlException extends RuntimeException {

    private final transient RestaurantType restaurantType;

    public CafeteriaCrawlException(
            RestaurantType restaurantType,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.restaurantType = restaurantType;
    }

    public RestaurantType getRestaurantType() {
        return restaurantType;
    }
}
