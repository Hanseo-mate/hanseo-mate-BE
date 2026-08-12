package hsu.hanseomate.domain.cafeteria.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Python FastAPI 식단 크롤러 서버(http://34.64.250.12:8000)와 통신하는 HTTP 클라이언트.
 * 스케줄러가 크롤링 트리거를 호출할 때 사용한다.
 */
@Component
public class CafeteriaCrawlerClient {

    private static final Logger log = LoggerFactory.getLogger(CafeteriaCrawlerClient.class);

    private final RestClient restClient;

    public CafeteriaCrawlerClient(
            RestClient.Builder restClientBuilder,
            @Value("${cafeteria.crawler.api-base-url:http://34.64.250.12:8000}") String baseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    // ─── Trigger ──────────────────────────────────────────────────────────────

    /**
     * POST /cafeteria-crawl/run
     * <p>주어진 URL 과 식당 유형으로 크롤링을 백그라운드로 트리거한다.</p>
     *
     * @param url            크롤링 대상 URL
     * @param restaurantType 식당 유형 문자열 (예: "MAIN_STUDENT")
     */
    public void triggerCrawl(String url, String restaurantType) {
        try {
            restClient.post()
                    .uri("/cafeteria-crawl/run")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(CafeteriaCrawlRunRequest.background(url, restaurantType))
                    .retrieve()
                    .toBodilessEntity();

            log.info("[CafeteriaCrawler] Triggered crawl: restaurantType={}, url={}", restaurantType, url);
        } catch (ResourceAccessException ex) {
            log.error("[CafeteriaCrawler] Crawler server unreachable (restaurantType={}): {}", restaurantType, ex.getMessage());
        } catch (RestClientException ex) {
            log.error("[CafeteriaCrawler] Failed to trigger crawl (restaurantType={}): {}", restaurantType, ex.getMessage());
        }
    }

    // ─── Health & Status ──────────────────────────────────────────────────────

    /**
     * GET /health — 크롤러 서버 헬스 체크.
     *
     * @return 헬스 응답, 연결 실패 시 null
     */
    public CafeteriaHealthResponse checkHealth() {
        try {
            return restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(CafeteriaHealthResponse.class);
        } catch (RestClientException ex) {
            log.warn("[CafeteriaCrawler] Health check failed: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * GET /cafeteria-crawl/status — 마지막 크롤링 상태 확인.
     *
     * @return 상태 응답, 연결 실패 시 null
     */
    public CafeteriaCrawlStatusResponse checkStatus() {
        try {
            return restClient.get()
                    .uri("/cafeteria-crawl/status")
                    .retrieve()
                    .body(CafeteriaCrawlStatusResponse.class);
        } catch (RestClientException ex) {
            log.warn("[CafeteriaCrawler] Status check failed: {}", ex.getMessage());
            return null;
        }
    }
}
