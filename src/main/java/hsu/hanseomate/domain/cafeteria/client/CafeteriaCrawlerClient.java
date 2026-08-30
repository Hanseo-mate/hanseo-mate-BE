package hsu.hanseomate.domain.cafeteria.client;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Python FastAPI 식단 크롤러 서버(http://34.64.250.12:8000)와 통신하는 HTTP 클라이언트.
 * <p>
 * 전체 식당 일괄 크롤링 트리거({@link #triggerAllCrawl}), 개별 식당 트리거({@link #triggerCrawl}),
 * 파싱 결과를 동기로 받아오는 {@link #fetchMenus}를 제공한다.
 * DB 저장/비교/재시도는 Spring(Orchestrator)이 담당한다.
 */
@Component
public class CafeteriaCrawlerClient {

    private static final Logger log = LoggerFactory.getLogger(CafeteriaCrawlerClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;

    public CafeteriaCrawlerClient(
            RestClient.Builder restClientBuilder,
            @Value("${cafeteria.crawler.api-base-url:http://34.64.250.12:8000}") String baseUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    // ─── Trigger (전체 일괄) ───────────────────────────────────────────────────

    /**
     * POST /cafeteria-crawl/run (mode=background, restaurant_types 생략)
     * <p>4개 식당 전체를 한 번에 백그라운드로 크롤링 트리거하고 즉시 반환한다.
     * Python 크롤러 내부에서 2시간 간격 최대 5회 재시도를 자동 처리하므로
     * Spring 쪽에서는 재시도 로직이 불필요하다.</p>
     *
     * @throws org.springframework.web.client.HttpClientErrorException.Conflict 이미 실행 중(409) — 호출자에서 경고 로그 처리
     */
    public void triggerAllCrawl() {
        restClient.post()
                .uri("/cafeteria-crawl/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body(CafeteriaCrawlRunRequest.backgroundAll())
                .retrieve()
                .toBodilessEntity();

        log.info("[CafeteriaCrawler] Triggered full cafeteria crawl (mode=background, all restaurants)");
    }

    // ─── Trigger (개별 식당) ───────────────────────────────────────────────────

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

    // ─── Fetch (sync) ─────────────────────────────────────────────────────────

    /**
     * POST /cafeteria-crawl/run (mode=sync)
     * <p>주어진 URL 로 크롤/파싱을 동기 실행하고 파싱된 식단을 반환한다. DB 저장은 하지 않는다.</p>
     *
     * @param url            크롤링 대상 URL
     * @param restaurantType 식당 유형
     * @return 파싱 결과
     * @throws CafeteriaCrawlException timeout/4xx/5xx/역직렬화 실패/빈 응답 시
     */
    public CafeteriaCrawlResult fetchMenus(String url, RestaurantType restaurantType) {
        try {
            CafeteriaCrawlResult result = restClient.post()
                    .uri("/cafeteria-crawl/run")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(CafeteriaCrawlRunRequest.sync(url, restaurantType.name()))
                    .retrieve()
                    .body(CafeteriaCrawlResult.class);

            if (result == null) {
                throw new CafeteriaCrawlException(
                        restaurantType,
                        "Empty crawl response body (restaurantType=" + restaurantType + ")",
                        null
                );
            }
            log.info(
                    "[CafeteriaCrawler] Fetched menus: restaurantType={}, status={}, days={}",
                    restaurantType, result.status(), result.menusOrEmpty().size()
            );
            return result;
        } catch (ResourceAccessException ex) {
            throw new CafeteriaCrawlException(
                    restaurantType,
                    "Crawler server unreachable/timeout (restaurantType=" + restaurantType + ")",
                    ex
            );
        } catch (RestClientException ex) {
            throw new CafeteriaCrawlException(
                    restaurantType,
                    "Crawl request failed (restaurantType=" + restaurantType + ")",
                    ex
            );
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
