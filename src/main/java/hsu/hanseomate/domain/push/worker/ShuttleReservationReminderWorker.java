package hsu.hanseomate.domain.push.worker;

import hsu.hanseomate.domain.academiccalendar.service.AcademicCalendarService;

import hsu.hanseomate.domain.push.service.NotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.shuttle-reservation-reminder.enabled", havingValue = "true", matchIfMissing = true)
public class ShuttleReservationReminderWorker {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationService notificationService;
    private final Clock clock;
    private final AcademicCalendarService academicCalendarService;

    @Scheduled(cron = "0 50 17 * * WED", zone = "Asia/Seoul")
    public void enqueueWeeklyReminder() {
        if (!academicCalendarService.isTeachingDate(LocalDate.now(clock.withZone(KOREA_ZONE)))) return;
        notificationService.enqueueShuttleReservationReminder(
                LocalDate.now(clock.withZone(KOREA_ZONE))
                        .atTime(18, 0).atZone(KOREA_ZONE).toOffsetDateTime()
        );
    }
}
