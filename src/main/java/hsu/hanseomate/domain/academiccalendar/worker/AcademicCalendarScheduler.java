package hsu.hanseomate.domain.academiccalendar.worker;
import hsu.hanseomate.domain.academiccalendar.service.AcademicCalendarSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "academic-calendar.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class AcademicCalendarScheduler {
    private final AcademicCalendarSyncService syncService;
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncCurrentYear() { syncService.sync(); }
}
