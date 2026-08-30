package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /cafeteria-crawl/run 요청 바디.
 * <p>
 * {@code url}과 {@code restaurantType}은 식당별 개별 요청 시 사용한다.
 * 전체 식당 일괄 크롤링 시에는 두 필드를 {@code null}로 두어 생략하고
 * {@code mode=background}만 전송하면 Python 크롤러가 4개 식당을 모두 실행한다.
 */
public record CafeteriaCrawlRunRequest(
        @JsonProperty("url") String url,
        @JsonProperty("restaurant_type") String restaurantType,
        @JsonProperty("mode") String mode
) {

    public static CafeteriaCrawlRunRequest background(String url, String restaurantType) {
        return new CafeteriaCrawlRunRequest(url, restaurantType, "background");
    }

    public static CafeteriaCrawlRunRequest sync(String url, String restaurantType) {
        return new CafeteriaCrawlRunRequest(url, restaurantType, "sync");
    }

    /**
     * 전체 식당 일괄 크롤링 요청 ({@code restaurant_types} 생략, {@code mode=background}).
     * Python 크롤러가 4개 식당(MAIN_STUDENT, MAIN_STAFF, TAEAN_STUDENT, TAEAN_STAFF)을
     * 한 번에 백그라운드로 실행하고 즉시 반환한다.
     */
    public static CafeteriaCrawlRunRequest backgroundAll() {
        return new CafeteriaCrawlRunRequest(null, null, "background");
    }
}
