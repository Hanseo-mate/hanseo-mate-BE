package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.LocalDate;
import java.util.List;

/**
 * Python FastAPI 크롤러가 파싱해 반환하는 날짜별 식단 DTO.
 * <p>
 * <b>가정(needs confirmation against real Python API):</b> {@code menuDate}
 * 는 {@code yyyy-MM-dd}, {@code restaurantType} 은 {@link RestaurantType} 값과
 * 동일한 문자열이라고 가정한다.
 * <p>
 * 이 DTO 는 JPA 엔티티와 절대 공유하지 않는다(순수 클라이언트 계약).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CafeteriaDailyMenuCrawlDto(
        LocalDate menuDate,
        RestaurantType restaurantType,
        List<CafeteriaMealSectionCrawlDto> mealSections
) {
}
