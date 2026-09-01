package hsu.hanseomate.domain.cafeteria.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 학식 2시간 재시도 전용 {@link ThreadPoolTaskScheduler} 설정.
 * <p>
 * 전역 {@code @EnableScheduling} 의 기본 스케줄러와 분리된 별도 풀을 사용하여,
 * 2시간 지연 재시도가 주간 크론 등 다른 스케줄 작업을 막지 않도록 한다.
 */
@Configuration
public class CafeteriaSchedulingConfig {

    private static final Logger log =
            LoggerFactory.getLogger(CafeteriaSchedulingConfig.class);

    public static final String TASK_SCHEDULER_BEAN = "cafeteriaTaskScheduler";

    @Bean(name = TASK_SCHEDULER_BEAN, destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler cafeteriaTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("cafeteria-retry-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setErrorHandler(throwable ->
                log.error("Unhandled cafeteria retry error", throwable));
        scheduler.initialize();
        return scheduler;
    }
}
