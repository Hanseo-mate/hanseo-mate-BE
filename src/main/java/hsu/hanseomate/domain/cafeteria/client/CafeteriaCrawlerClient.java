package hsu.hanseomate.domain.cafeteria.client;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Python 크롤러 실행 요청 및 상태 조회 클라이언트.
 * 수집/DB 저장/당일 완료 판정은 Python이 담당하며 Spring은 정기 호출 시각만 관리한다.
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

    /** 정기 실행: 식당 목록을 생략하고 당일 미완료 식당만 수집하도록 한 번 요청한다. */
    public CafeteriaCrawlStatusResponse triggerPendingCrawl() {
        return trigger(CafeteriaCrawlRunRequest.backgroundAllPending());
    }

    /** 수동 강제 실행: 당일 성공 식당도 다시 조회한다. */
    public CafeteriaCrawlStatusResponse triggerAllCrawl() {
        return trigger(CafeteriaCrawlRunRequest.backgroundAll());
    }

    /** 특정 식당 요청. URL은 크롤러 설정을 사용한다. */
    public CafeteriaCrawlStatusResponse triggerCrawl(
            List<RestaurantType> restaurantTypes, boolean onlyPending
    ) {
        return trigger(CafeteriaCrawlRunRequest.background(restaurantTypes, onlyPending));
    }

    private CafeteriaCrawlStatusResponse trigger(CafeteriaCrawlRunRequest request) {
        CafeteriaCrawlStatusResponse response = restClient.post()
                .uri("/cafeteria-crawl/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CafeteriaCrawlStatusResponse.class);
        if (response == null) {
            throw new RestClientException("식단 크롤링 접수 응답이 비어 있습니다.");
        }
        log.info("[CafeteriaCrawler] 식단 크롤링 요청 접수: status={}, run_id={}, only_pending={}",
                response.status(), response.runId(), request.onlyPending());
        return response;
    }

    /** 크롤러 서버 헬스 체크. 연결 실패 시 null. */
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

    /** 현재 프로세스의 최근 실행 상태. 연결 실패 시 null. */
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

    /** DB에 보존된 당일 식당별 상태. 정기 호출의 사전 생략 조건으로 사용하지 않는다. */
    public CafeteriaDailyStatusResponse checkDailyStatus() {
        try {
            return restClient.get()
                    .uri("/cafeteria-crawl/daily-status")
                    .retrieve()
                    .body(CafeteriaDailyStatusResponse.class);
        } catch (RestClientException ex) {
            log.warn("[CafeteriaCrawler] Daily status check failed: {}", ex.getMessage());
            return null;
        }
    }
}
