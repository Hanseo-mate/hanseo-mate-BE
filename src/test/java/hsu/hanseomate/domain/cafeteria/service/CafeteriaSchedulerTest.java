package hsu.hanseomate.domain.cafeteria.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

class CafeteriaSchedulerTest {
    private Scheduled schedule() throws NoSuchMethodException {
        return CafeteriaScheduler.class.getMethod("triggerPendingCrawl")
                .getAnnotation(Scheduled.class);
    }

    @Test
    void exactlyNineTimesEveryWeekdayInKorea() throws Exception {
        Scheduled scheduled = schedule();
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        CronExpression cron = CronExpression.parse(scheduled.cron());
        ZonedDateTime cursor = LocalDate.of(2026, 10, 5)
                .atStartOfDay(ZoneId.of(scheduled.zone())).minusSeconds(1);
        List<ZonedDateTime> actual = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            cursor = cron.next(cursor);
            actual.add(cursor);
        }
        List<ZonedDateTime> expected = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            for (int hour = 1; hour <= 17; hour += 2) {
                expected.add(LocalDate.of(2026, 10, 5).plusDays(day)
                        .atTime(hour, 0).atZone(ZoneId.of("Asia/Seoul")));
            }
        }
        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(cron.next(cursor)).isEqualTo(
                ZonedDateTime.parse("2026-10-12T01:00:00+09:00[Asia/Seoul]"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-06T01:15:00Z, 2026-10-06T02:00:00Z",
            "2026-10-06T08:30:00Z, 2026-10-06T16:00:00Z",
            "2026-10-09T08:00:00Z, 2026-10-11T16:00:00Z",
            "2026-10-10T00:00:00Z, 2026-10-11T16:00:00Z",
            "2026-10-11T10:00:00Z, 2026-10-11T16:00:00Z"
    })
    void deploymentUsesNextSlotEvenWhenHostClockIsUtc(String now, String expected)
            throws Exception {
        Scheduled scheduled = schedule();
        CronTrigger trigger = new CronTrigger(scheduled.cron(), ZoneId.of(scheduled.zone()));
        SimpleTriggerContext context = new SimpleTriggerContext(
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
        assertThat(trigger.nextExecution(context)).isEqualTo(Instant.parse(expected));
    }
}
