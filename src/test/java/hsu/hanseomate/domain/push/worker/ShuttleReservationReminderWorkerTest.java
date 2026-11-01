package hsu.hanseomate.domain.push.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import hsu.hanseomate.domain.push.service.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

class ShuttleReservationReminderWorkerTest {

    @ParameterizedTest
    @CsvSource({
            "2026-10-11T00:00:00Z, 2026-10-14T08:50:00Z",
            "2026-10-14T08:49:59Z, 2026-10-14T08:50:00Z",
            "2026-10-14T08:50:00Z, 2026-10-21T08:50:00Z",
            "2026-10-14T09:00:00Z, 2026-10-21T08:50:00Z"
    })
    void schedulesOnlyWednesdayAt1750InKoreaEvenWithUtcHostClock(String now, String expected)
            throws Exception {
        Scheduled schedule = ShuttleReservationReminderWorker.class
                .getMethod("enqueueWeeklyReminder").getAnnotation(Scheduled.class);
        assertThat(schedule.zone()).isEqualTo("Asia/Seoul");
        CronTrigger trigger = new CronTrigger(schedule.cron(), ZoneId.of(schedule.zone()));
        SimpleTriggerContext context = new SimpleTriggerContext(
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));

        assertThat(trigger.nextExecution(context)).isEqualTo(Instant.parse(expected));
    }

    @Test
    void enqueuesReminderForTheSameWednesdayAt1800InKorea() {
        NotificationService service = mock(NotificationService.class);
        ShuttleReservationReminderWorker worker = new ShuttleReservationReminderWorker(
                service, Clock.fixed(Instant.parse("2026-10-14T08:50:00Z"), ZoneOffset.UTC));

        worker.enqueueWeeklyReminder();

        verify(service).enqueueShuttleReservationReminder(
                OffsetDateTime.parse("2026-10-14T18:00:00+09:00"));
    }
}
