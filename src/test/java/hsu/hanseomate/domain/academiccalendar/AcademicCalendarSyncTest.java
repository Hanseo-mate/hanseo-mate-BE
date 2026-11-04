package hsu.hanseomate.domain.academiccalendar;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import hsu.hanseomate.domain.academiccalendar.client.AcademicCalendarCrawlerClient;
import hsu.hanseomate.domain.academiccalendar.service.*;
import hsu.hanseomate.domain.academiccalendar.worker.AcademicCalendarScheduler;
import hsu.hanseomate.domain.academiccalendar.repository.HolidayMonthRepository;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
class AcademicCalendarSyncTest {
    @Test void onlyKoreanCurrentCalendarYearIsRequestedEvenAtNewYear() throws Exception {
        var crawler=mock(AcademicCalendarCrawlerClient.class);
        var holidays=mock(HolidaySyncService.class);
        when(crawler.request(2027)).thenReturn(new AcademicCalendarCrawlerClient.CrawlReceipt("starting","run-1"));
        doThrow(new IllegalStateException()).when(holidays).sync(YearMonth.of(2028,3));
        var service=new AcademicCalendarSyncService(crawler,holidays,
                Clock.fixed(Instant.parse("2026-12-31T19:00:00Z"),ZoneOffset.UTC));
        var result=service.sync();
        verify(crawler).request(2027);
        verifyNoMoreInteractions(crawler);
        assertThat(result.crawlStatus()).isEqualTo("ACCEPTED");
        assertThat(result.failedHolidayMonths()).containsExactly("2028-03");
        Scheduled scheduled=AcademicCalendarScheduler.class.getMethod("syncCurrentYear").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 0 4 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test void offlineSnapshotIncludesAdditionalHolidaysAndUsesUtcTimestamp() {
        var repository = mock(HolidayMonthRepository.class);
        var sync = new HolidaySyncService(new KoreanHolidayCalendar("2026-03-03"), repository,
                Clock.fixed(Instant.parse("2026-02-28T19:00:00Z"), ZoneId.of("Asia/Seoul")));
        sync.sync(YearMonth.of(2026, 3));
        verify(repository).save(argThat(snapshot ->
                snapshot.getMonthKey().equals("2026-03")
                        && snapshot.dates().contains(LocalDate.of(2026, 3, 2))
                        && snapshot.dates().contains(LocalDate.of(2026, 3, 3))
                        && snapshot.getSyncedAt().equals(LocalDateTime.of(2026, 2, 28, 19, 0))));
        clearInvocations(repository);
        assertThatThrownBy(() -> sync.sync(YearMonth.of(2036, 3)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
}
