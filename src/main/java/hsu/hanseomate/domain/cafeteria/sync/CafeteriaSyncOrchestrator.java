package hsu.hanseomate.domain.cafeteria.sync;

import hsu.hanseomate.domain.cafeteria.client.CafeteriaCrawlException;
import hsu.hanseomate.domain.cafeteria.client.CafeteriaCrawlResult;
import hsu.hanseomate.domain.cafeteria.client.CafeteriaCrawlerClient;
import hsu.hanseomate.domain.cafeteria.client.CafeteriaDailyMenuCrawlDto;
import hsu.hanseomate.domain.cafeteria.config.CafeteriaSchedulingConfig;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.repository.DailyMenuRepository;
import hsu.hanseomate.domain.cafeteria.retry.CafeteriaRetryStateService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 학식 동기화 오케스트레이터(비트랜잭셔널).
 * <p>
 * HTTP 로 Python 크롤러의 파싱 결과를 받아 DB 스냅샷과 전체 내용 비교 후,
 * 다르면 트랜잭셔널 서비스({@link CafeteriaMenuReplaceTransactionService})로 delete+insert,
 * 같으면 {@link CafeteriaRetryStateService} 를 통해 2시간 뒤 재시도를 예약한다.
 * HTTP 호출 동안 DB 트랜잭션을 열어두지 않으며, 자기 자신을 직접 호출하지 않는다.
 */
@Component
public class CafeteriaSyncOrchestrator {

    private static final Logger log =
            LoggerFactory.getLogger(CafeteriaSyncOrchestrator.class);

    private final CafeteriaCrawlerClient crawlerClient;
    private final CafeteriaMenuReplaceTransactionService replaceService;
    private final DailyMenuRepository dailyMenuRepository;
    private final CafeteriaRetryStateService retryStateService;
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final Map<RestaurantType, String> crawlUrls;

    public CafeteriaSyncOrchestrator(
            CafeteriaCrawlerClient crawlerClient,
            CafeteriaMenuReplaceTransactionService replaceService,
            DailyMenuRepository dailyMenuRepository,
            CafeteriaRetryStateService retryStateService,
            @Qualifier(CafeteriaSchedulingConfig.TASK_SCHEDULER_BEAN)
            TaskScheduler taskScheduler,
            Clock clock,
            @Value("${cafeteria.crawler.main-student-url}") String mainStudentUrl,
            @Value("${cafeteria.crawler.main-staff-url}") String mainStaffUrl,
            @Value("${cafeteria.crawler.taean-student-url}") String taeanStudentUrl,
            @Value("${cafeteria.crawler.taean-staff-url}") String taeanStaffUrl
    ) {
        this.crawlerClient = crawlerClient;
        this.replaceService = replaceService;
        this.dailyMenuRepository = dailyMenuRepository;
        this.retryStateService = retryStateService;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.crawlUrls = new EnumMap<>(RestaurantType.class);
        this.crawlUrls.put(RestaurantType.MAIN_STUDENT, mainStudentUrl);
        this.crawlUrls.put(RestaurantType.MAIN_STAFF, mainStaffUrl);
        this.crawlUrls.put(RestaurantType.TAEAN_STUDENT, taeanStudentUrl);
        this.crawlUrls.put(RestaurantType.TAEAN_STAFF, taeanStaffUrl);
    }

    /**
     * 즉시 동기화를 전용 스케줄러 스레드로 넘겨 비동기 실행한다(호출 스레드 미차단).
     */
    public void triggerAsync(RestaurantType restaurantType) {
        taskScheduler.schedule(() -> sync(restaurantType), Instant.now(clock));
    }

    /**
     * 한 식당의 동기화를 실행한다. 같은 식당이 이미 실행 중이면 no-op.
     */
    public void sync(RestaurantType restaurantType) {
        if (!retryStateService.beginRun(restaurantType)) {
            log.info("[CafeteriaSync] Already running, skip: restaurantType={}",
                    restaurantType);
            return;
        }
        try {
            SyncOutcome outcome = execute(restaurantType);
            switch (outcome.status()) {
                case CHANGED -> retryStateService.onChanged(restaurantType);
                case UNCHANGED -> retryStateService.onUnchanged(
                        restaurantType, () -> sync(restaurantType));
                case FAILED -> retryStateService.onFailure(
                        restaurantType, () -> sync(restaurantType), outcome.error());
                default -> throw new IllegalStateException(
                        "Unknown status: " + outcome.status());
            }
        } finally {
            retryStateService.endRun(restaurantType);
        }
    }

    private SyncOutcome execute(RestaurantType restaurantType) {
        CafeteriaCrawlResult result;
        try {
            result = crawlerClient.fetchMenus(urlFor(restaurantType), restaurantType);
        } catch (CafeteriaCrawlException ex) {
            log.error(
                    "[CafeteriaSync] Crawl call failed, DB untouched:"
                            + " restaurantType={}, error={}",
                    restaurantType, ex.getMessage(), ex
            );
            return SyncOutcome.failed(ex.getMessage());
        }

        List<CafeteriaDailyMenuCrawlDto> menus = result.menusOrEmpty();
        if (menus.isEmpty()) {
            log.error(
                    "[CafeteriaSync] Empty crawl result, DB untouched:"
                            + " restaurantType={}, status={}",
                    restaurantType, result.status()
            );
            return SyncOutcome.failed("empty crawl result");
        }

        List<DailyMenu> existing = dailyMenuRepository
                .findAllByRestaurantTypeOrderByMenuDateAscIdAsc(restaurantType);
        CafeteriaMenuSnapshot dbSnapshot =
                CafeteriaMenuSnapshot.fromEntities(restaurantType, existing);
        CafeteriaMenuSnapshot crawlSnapshot =
                CafeteriaMenuSnapshot.fromCrawl(restaurantType, menus);

        boolean identical =
                dbSnapshot.contentHash().equals(crawlSnapshot.contentHash())
                        && dbSnapshot.equals(crawlSnapshot);
        if (identical) {
            log.info("[CafeteriaSync] No change detected: restaurantType={}", restaurantType);
            return SyncOutcome.unchanged();
        }

        replaceService.replaceWeek(restaurantType, menus);
        log.info("[CafeteriaSync] Change applied: restaurantType={}, days={}",
                restaurantType, menus.size());
        return SyncOutcome.changed();
    }

    private String urlFor(RestaurantType restaurantType) {
        String url = crawlUrls.get(restaurantType);
        if (url == null) {
            throw new IllegalArgumentException(
                    "No crawl URL configured for " + restaurantType);
        }
        return url;
    }

    private enum SyncStatus {
        CHANGED,
        UNCHANGED,
        FAILED
    }

    private record SyncOutcome(SyncStatus status, String error) {
        static SyncOutcome changed() {
            return new SyncOutcome(SyncStatus.CHANGED, null);
        }

        static SyncOutcome unchanged() {
            return new SyncOutcome(SyncStatus.UNCHANGED, null);
        }

        static SyncOutcome failed(String error) {
            return new SyncOutcome(SyncStatus.FAILED, error);
        }
    }
}
