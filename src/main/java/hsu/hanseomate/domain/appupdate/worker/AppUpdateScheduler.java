package hsu.hanseomate.domain.appupdate.worker;

import hsu.hanseomate.domain.appupdate.repository.AppUpdatePolicyRepository;
import hsu.hanseomate.domain.appupdate.service.AppUpdatePolicyOperations;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.updates.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class AppUpdateScheduler {
    private final AppUpdatePolicyRepository policies;
    private final AppUpdatePolicyOperations operations;
    private final Clock clock;
    private final MeterRegistry metrics;

    @Scheduled(fixedDelayString = "${app.updates.scheduler-delay-ms:1000}",
            initialDelayString = "${app.updates.scheduler-delay-ms:1000}")
    public void activateDuePolicies() {
        List<Long> due;
        try {
            due = policies.findDueIds(clock.instant());
        } catch (RuntimeException failure) {
            metrics.counter("app.update.scheduler.failures", "stage", "query").increment();
            log.error("App update scheduler cannot query due policies; next cycle will retry", failure);
            return;
        }
        for (long id : due) {
            try {
                if (operations.activateDue(id)) metrics.counter("app.update.scheduler.activations").increment();
            } catch (RuntimeException failure) {
                metrics.counter("app.update.scheduler.failures", "stage", "activate").increment();
                log.error("App update scheduled activation failed; existing policy retained, next cycle will retry: policyId={}", id, failure);
            }
        }
    }
}
