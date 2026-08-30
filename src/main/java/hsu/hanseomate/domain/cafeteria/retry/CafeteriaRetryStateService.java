package hsu.hanseomate.domain.cafeteria.retry;

import hsu.hanseomate.domain.cafeteria.config.CafeteriaSchedulingConfig;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * 식당 유형별 재시도 상태를 <b>인메모리</b>로 관리한다.
 * <p>
 * "unchanged"(또는 HTTP/파싱 실패)가 감지되면 동일 식당의 동기화를 정확히 2시간 뒤로
 * {@link TaskScheduler} 에 예약한다({@code Thread.sleep} 미사용). 예약 시각 계산에는
 * 주입된 {@link Clock} 을 사용하여 테스트에서 시간을 제어할 수 있다. 최대 재시도 5회.
 * <p>
 * 동시성: 식당별 {@link ReentrantLock}({@link #beginRun}/{@link #endRun})으로 같은 식당의
 * 중복 실행을 막는다(두 번째 동시 호출은 no-op). 서로 다른 식당은 서로를 막지 않는다.
 * 새 트리거 시 기존 예약된 {@link ScheduledFuture} 를 먼저 취소하여 중복 예약을 방지한다.
 * <p>
 * <b>한계(중요):</b> 이 {@code ScheduledFuture} 상태는 인메모리이므로 앱 재시작/재배포 시,
 * 그리고 다중 인스턴스 배포 시 유실·중복될 수 있다. 재시작·다중 인스턴스 안전이 필요하면
 * DB 기반 재시도 잡 테이블 또는 분산 락(ShedLock 등)으로 전환해야 한다. 현재 코드베이스에는
 * ShedLock/분산 락이 존재하지 않아 인메모리로 구현했다.
 */
@Service
public class CafeteriaRetryStateService {

    public static final int MAX_RETRY_COUNT = 5;
    public static final Duration RETRY_DELAY = Duration.ofHours(2);

    private static final Logger log =
            LoggerFactory.getLogger(CafeteriaRetryStateService.class);

    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final ConcurrentHashMap<RestaurantType, RetryState> states =
            new ConcurrentHashMap<>();

    public CafeteriaRetryStateService(
            @Qualifier(CafeteriaSchedulingConfig.TASK_SCHEDULER_BEAN)
            TaskScheduler taskScheduler,
            Clock clock
    ) {
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    /**
     * 같은 식당의 동기화 실행 권한을 획득한다. 이미 실행 중이면 false(호출자는 no-op).
     */
    public boolean beginRun(RestaurantType restaurantType) {
        return state(restaurantType).runLock.tryLock();
    }

    /**
     * {@link #beginRun} 으로 획득한 실행 권한을 반납한다.
     */
    public void endRun(RestaurantType restaurantType) {
        RetryState state = state(restaurantType);
        if (state.runLock.isHeldByCurrentThread()) {
            state.runLock.unlock();
        }
    }

    /**
     * 실제 변경 반영 성공: 재시도 카운터 초기화 + 예약된 재시도 취소.
     */
    public void onChanged(RestaurantType restaurantType) {
        RetryState state = state(restaurantType);
        synchronized (state) {
            cancelFuture(state);
            state.retryCount = 0;
            state.nextRetryAt = null;
            state.lastResult = "CHANGED";
            state.lastError = null;
            log.info("[CafeteriaRetry] Change applied, retry state reset: restaurantType={}",
                    restaurantType);
        }
    }

    /**
     * 내용 동일(unchanged): 2시간 뒤 재시도 예약(최대 5회).
     */
    public void onUnchanged(RestaurantType restaurantType, Runnable retryTask) {
        scheduleRetryOrGiveUp(restaurantType, retryTask, "UNCHANGED", null);
    }

    /**
     * HTTP/파싱 실패: unchanged 와 동일한 재시도 카운터를 공유하여 재시도 예약한다.
     * (문서상 "HTTP 실패도 재시도 대상으로 포함" 을 기본 채택 — 최종 리포트에 명시)
     */
    public void onFailure(RestaurantType restaurantType, Runnable retryTask, String error) {
        scheduleRetryOrGiveUp(restaurantType, retryTask, "FAILED", error);
    }

    private void scheduleRetryOrGiveUp(
            RestaurantType restaurantType,
            Runnable retryTask,
            String result,
            String error
    ) {
        RetryState state = state(restaurantType);
        synchronized (state) {
            state.lastResult = result;
            state.lastError = error;
            // 중복 예약 방지: 기존 대기 중 future 취소.
            cancelFuture(state);

            if (state.retryCount >= MAX_RETRY_COUNT) {
                state.nextRetryAt = null;
                log.warn(
                        "[CafeteriaRetry] Max retries ({}) reached, giving up:"
                                + " restaurantType={}, lastResult={}",
                        MAX_RETRY_COUNT, restaurantType, result
                );
                return;
            }

            Instant runAt = Instant.now(clock).plus(RETRY_DELAY);
            state.future = taskScheduler.schedule(wrap(restaurantType, retryTask), runAt);
            state.retryCount++;
            state.nextRetryAt = runAt;
            log.info(
                    "[CafeteriaRetry] Scheduled retry #{} at {}: restaurantType={}, reason={}",
                    state.retryCount, runAt, restaurantType, result
            );
        }
    }

    private Runnable wrap(RestaurantType restaurantType, Runnable retryTask) {
        // 스케줄러 스레드로 예외가 새어나가지 않도록 감싼다.
        return () -> {
            try {
                retryTask.run();
            } catch (RuntimeException ex) {
                log.error("Unhandled cafeteria retry error (restaurantType={})",
                        restaurantType, ex);
            }
        };
    }

    private void cancelFuture(RetryState state) {
        if (state.future != null) {
            state.future.cancel(false);
            state.future = null;
        }
    }

    private RetryState state(RestaurantType restaurantType) {
        return states.computeIfAbsent(restaurantType, key -> new RetryState());
    }

    // ─── 테스트/모니터링용 조회 ────────────────────────────────────────────────

    public int retryCount(RestaurantType restaurantType) {
        return state(restaurantType).retryCount;
    }

    public Instant nextRetryAt(RestaurantType restaurantType) {
        return state(restaurantType).nextRetryAt;
    }

    private static final class RetryState {
        private final ReentrantLock runLock = new ReentrantLock();
        private int retryCount;
        private Instant nextRetryAt;
        private ScheduledFuture<?> future;
        private String lastResult;
        private String lastError;
    }
}
