package hsu.hanseomate.domain.cafeteria.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hsu.hanseomate.domain.cafeteria.client.CafeteriaCrawlException;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

/**
 * {@link CafeteriaSyncOrchestrator} + {@link CafeteriaRetryStateService} 조합 단위 테스트.
 * TaskScheduler 는 mock, Clock 은 고정하여 시간을 통제한다.
 */
class CafeteriaSyncOrchestratorTest {

    private static final RestaurantType TYPE = RestaurantType.MAIN_STUDENT;
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private static final String URL = "http://localhost/main-student";

    private CafeteriaCrawlerClient crawlerClient;
    private CafeteriaMenuReplaceTransactionService replaceService;
    private DailyMenuRepository dailyMenuRepository;
    private TaskScheduler taskScheduler;
    private CafeteriaRetryStateService retryStateService;
    private CafeteriaSyncOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        crawlerClient = mock(CafeteriaCrawlerClient.class);
        replaceService = mock(CafeteriaMenuReplaceTransactionService.class);
        dailyMenuRepository = mock(DailyMenuRepository.class);
        taskScheduler = mock(TaskScheduler.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        retryStateService = new CafeteriaRetryStateService(taskScheduler, clock);
        orchestrator = new CafeteriaSyncOrchestrator(
                crawlerClient,
                replaceService,
                dailyMenuRepository,
                retryStateService,
                taskScheduler,
                clock,
                URL, "u2", "u3", "u4"
        );
    }

    private CafeteriaDailyMenuCrawlDto sampleDay() {
        return new CafeteriaDailyMenuCrawlDto(
                java.time.LocalDate.of(2026, 8, 31),
                TYPE,
                List.of(new CafeteriaMealSectionCrawlDto(
                        MealTime.LUNCH, "한식코너", 5500,
                        List.of("제육볶음", "된장국"), "한식코너\n제육볶음\n된장국"))
        );
    }

    private DailyMenu sampleDbMenu() {
        DailyMenu menu = DailyMenu.of(TYPE, java.time.LocalDate.of(2026, 8, 31));
        menu.addMealSection(MealTime.LUNCH, "한식코너", 5500,
                List.of("제육볶음", "된장국"), "한식코너\n제육볶음\n된장국");
        return menu;
    }

    private CafeteriaCrawlResult resultWith(List<CafeteriaDailyMenuCrawlDto> menus) {
        return new CafeteriaCrawlResult(
                "run-1", "completed", true, 0, 5, null, menus);
    }

    @Test
    void identicalData_doesNotInvokeReplaceTransaction() {
        when(crawlerClient.fetchMenus(URL, TYPE))
                .thenReturn(resultWith(List.of(sampleDay())));
        when(dailyMenuRepository.findAllByRestaurantTypeOrderByMenuDateAscIdAsc(TYPE))
                .thenReturn(List.of(sampleDbMenu()));

        orchestrator.sync(TYPE);

        verifyNoInteractions(replaceService);
    }

    @Test
    void identicalData_schedulesExactlyOneRetryTwoHoursLater() {
        when(crawlerClient.fetchMenus(URL, TYPE))
                .thenReturn(resultWith(List.of(sampleDay())));
        when(dailyMenuRepository.findAllByRestaurantTypeOrderByMenuDateAscIdAsc(TYPE))
                .thenReturn(List.of(sampleDbMenu()));

        orchestrator.sync(TYPE);

        Instant expected = NOW.plus(Duration.ofHours(2));
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), eq(expected));
        assertThat(retryStateService.retryCount(TYPE)).isEqualTo(1);
        assertThat(retryStateService.nextRetryAt(TYPE)).isEqualTo(expected);
    }

    @Test
    void changedData_invokesReplaceAndResetsRetryState() {
        CafeteriaDailyMenuCrawlDto changed = new CafeteriaDailyMenuCrawlDto(
                java.time.LocalDate.of(2026, 8, 31),
                TYPE,
                List.of(new CafeteriaMealSectionCrawlDto(
                        MealTime.LUNCH, "한식코너", 6000,
                        List.of("불고기"), "한식코너\n불고기")));
        when(crawlerClient.fetchMenus(URL, TYPE))
                .thenReturn(resultWith(List.of(changed)));
        when(dailyMenuRepository.findAllByRestaurantTypeOrderByMenuDateAscIdAsc(TYPE))
                .thenReturn(List.of(sampleDbMenu()));

        orchestrator.sync(TYPE);

        verify(replaceService, times(1)).replaceWeek(eq(TYPE), any());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(retryStateService.retryCount(TYPE)).isZero();
    }

    @Test
    void emptyCrawlResult_doesNotDeleteAndCountsAsRetry() {
        when(crawlerClient.fetchMenus(URL, TYPE))
                .thenReturn(resultWith(List.of()));

        orchestrator.sync(TYPE);

        verifyNoInteractions(replaceService);
        // empty 는 실패로 취급되어 재시도 카운터에 포함된다.
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        assertThat(retryStateService.retryCount(TYPE)).isEqualTo(1);
    }

    @Test
    void httpFailure_isHandledAndCountedAsRetryWithoutDeletingDb() {
        when(crawlerClient.fetchMenus(URL, TYPE))
                .thenThrow(new CafeteriaCrawlException(TYPE, "5xx", new RuntimeException()));

        orchestrator.sync(TYPE);

        verifyNoInteractions(replaceService);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        assertThat(retryStateService.retryCount(TYPE)).isEqualTo(1);
    }

    @Test
    void afterFiveRetries_noFurtherScheduleHappens() {
        when(crawlerClient.fetchMenus(URL, TYPE))
                .thenReturn(resultWith(List.of(sampleDay())));
        when(dailyMenuRepository.findAllByRestaurantTypeOrderByMenuDateAscIdAsc(TYPE))
                .thenReturn(List.of(sampleDbMenu()));

        // 5회 unchanged → 5회 예약.
        for (int i = 0; i < 5; i++) {
            orchestrator.sync(TYPE);
        }
        verify(taskScheduler, times(5)).schedule(any(Runnable.class), any(Instant.class));
        assertThat(retryStateService.retryCount(TYPE)).isEqualTo(5);

        // 6번째 unchanged → 더 이상 예약하지 않음(총 5회 유지).
        orchestrator.sync(TYPE);
        verify(taskScheduler, times(5)).schedule(any(Runnable.class), any(Instant.class));
        assertThat(retryStateService.retryCount(TYPE)).isEqualTo(5);
    }
}
