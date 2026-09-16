package hsu.hanseomate.domain.cafeteria.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hsu.hanseomate.domain.cafeteria.client.CafeteriaCrawlResult;
import hsu.hanseomate.domain.cafeteria.client.CafeteriaCrawlerClient;
import hsu.hanseomate.domain.cafeteria.client.CafeteriaDailyMenuCrawlDto;
import hsu.hanseomate.domain.cafeteria.client.CafeteriaMealSectionCrawlDto;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.repository.DailyMenuRepository;
import hsu.hanseomate.domain.cafeteria.retry.CafeteriaRetryStateService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

/**
 * 같은 식당에 대한 동시 sync 호출이 replace 로직을 병렬로 실행하지 않음을 증명한다.
 */
class CafeteriaSyncConcurrencyTest {

    private static final RestaurantType TYPE = RestaurantType.MAIN_STUDENT;
    private static final String URL = "http://localhost/main-student";

    @Test
    void concurrentSyncForSameRestaurant_runsReplaceOnlyOnce() throws Exception {
        CafeteriaCrawlerClient crawlerClient = mock(CafeteriaCrawlerClient.class);
        CafeteriaMenuReplaceTransactionService replaceService =
                mock(CafeteriaMenuReplaceTransactionService.class);
        DailyMenuRepository dailyMenuRepository = mock(DailyMenuRepository.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC);
        CafeteriaRetryStateService retryStateService =
                new CafeteriaRetryStateService(taskScheduler, clock);

        CafeteriaSyncOrchestrator orchestrator = new CafeteriaSyncOrchestrator(
                crawlerClient, replaceService, dailyMenuRepository,
                retryStateService, taskScheduler, clock,
                URL, "u2", "u3", "u4"
        );

        CafeteriaDailyMenuCrawlDto changed = new CafeteriaDailyMenuCrawlDto(
                LocalDate.of(2026, 8, 31), TYPE,
                List.of(new CafeteriaMealSectionCrawlDto(
                        MealTime.LUNCH, "코너", 6000, List.of("불고기"), "raw")));
        when(crawlerClient.fetchMenus(URL, TYPE))
                .thenReturn(new CafeteriaCrawlResult(
                        "run", "completed", true, 0, 5, null, List.of(changed)));

        DailyMenu dbMenu = DailyMenu.of(TYPE, LocalDate.of(2026, 8, 31));
        dbMenu.addMealSection(MealTime.LUNCH, "코너", 5000, List.of("제육"), "raw");
        when(dailyMenuRepository.findAllByRestaurantTypeOrderByMenuDateAscIdAsc(TYPE))
                .thenReturn(List.of(dbMenu));

        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger replaceInvocations = new AtomicInteger();
        // 첫 호출은 replaceWeek 안에서 잠시 머무르며 두 번째 스레드가 진입을 시도하게 한다.
        org.mockito.Mockito.doAnswer(invocation -> {
            replaceInvocations.incrementAndGet();
            inside.countDown();
            release.await(2, TimeUnit.SECONDS);
            return null;
        }).when(replaceService).replaceWeek(eq(TYPE), any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> orchestrator.sync(TYPE));
            // 첫 스레드가 replaceWeek 내부에 도달할 때까지 대기.
            assertThat(inside.await(2, TimeUnit.SECONDS)).isTrue();
            // 두 번째 동시 호출 — 락을 얻지 못해 no-op 이어야 한다.
            executor.submit(() -> orchestrator.sync(TYPE)).get(2, TimeUnit.SECONDS);
            release.countDown();
        } finally {
            executor.shutdown();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }

        verify(replaceService, times(1)).replaceWeek(eq(TYPE), any());
        assertThat(replaceInvocations.get()).isEqualTo(1);
    }
}
