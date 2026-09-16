package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * POST /cafeteria-crawl/run (mode=sync) 동기 응답 바디.
 * <p>
 * <b>가정(needs confirmation against real Python API):</b> Python 이 파싱 결과를
 * {@code menus} 배열로 반환하며 {@code status} 는 {@code completed}/{@code unchanged}/
 * {@code failed} 등을 가질 수 있다고 가정한다. 스케줄링/재시도 카운터는 Spring 이 자체
 * 관리하므로 {@code retryCount}/{@code maxRetryCount}/{@code nextRetryAt} 는 참고용
 * (로그)으로만 사용한다. 알 수 없는 필드는 무시한다.
 * <p>
 * 이 DTO 는 JPA 엔티티와 절대 공유하지 않는다(순수 클라이언트 계약).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CafeteriaCrawlResult(
        @JsonProperty("run_id") String runId,
        @JsonProperty("status") String status,
        @JsonProperty("updated") Boolean updated,
        @JsonProperty("retryCount") Integer retryCount,
        @JsonProperty("maxRetryCount") Integer maxRetryCount,
        @JsonProperty("nextRetryAt") String nextRetryAt,
        @JsonProperty("menus") List<CafeteriaDailyMenuCrawlDto> menus
) {

    public List<CafeteriaDailyMenuCrawlDto> menusOrEmpty() {
        return menus == null ? List.of() : menus;
    }
}
