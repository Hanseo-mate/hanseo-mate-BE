package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import java.util.List;

/**
 * Python FastAPI 크롤러가 파싱해 반환하는 코너(식사 구역) 단위 DTO.
 * <p>
 * <b>가정(needs confirmation against real Python API):</b> Python 응답의
 * {@code mealSections[]} 배열 요소가 아래 필드(camelCase)를 포함한다고 가정한다.
 * 알 수 없는 필드는 무시하여 Python 이 필드를 추가해도 역직렬화가 깨지지 않는다.
 * <p>
 * 이 DTO 는 JPA 엔티티와 절대 공유하지 않는다(순수 클라이언트 계약).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CafeteriaMealSectionCrawlDto(
        MealTime mealTime,
        String cornerName,
        Integer price,
        List<String> dishes,
        String rawText
) {
}
