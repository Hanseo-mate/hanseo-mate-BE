package hsu.hanseomate.domain.academiccalendar.service;

import hsu.hanseomate.domain.academiccalendar.client.AcademicCalendarCrawlerClient;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicCalendarSyncService {
    private final AcademicCalendarCrawlerClient crawler;
    private final HolidaySyncService holidays;
    private final Clock clock;

    public SyncResult sync() {
        LocalDate today = LocalDate.now(clock.withZone(AcademicCalendarService.ZONE));
        int year = today.getYear();
        List<String> failedMonths = new ArrayList<>();
        for (YearMonth month : List.of(YearMonth.of(year, 3), YearMonth.of(year, 9), YearMonth.of(year + 1, 3))) {
            try { holidays.sync(month); }
            catch (RuntimeException e) {
                failedMonths.add(month.toString());
                log.warn("Holiday refresh failed for {} ({})", month, e.getClass().getSimpleName());
            }
        }
        try {
            var receipt = crawler.request(year);
            log.info("Academic calendar crawl accepted: year={}, runId={}", year, receipt.run_id());
            return new SyncResult(year, "ACCEPTED", receipt.run_id(), failedMonths, null);
        } catch (org.springframework.web.client.HttpClientErrorException.Conflict e) {
            return new SyncResult(year, "ALREADY_RUNNING", null, failedMonths, null);
        } catch (HttpStatusCodeException e) {
            String reason = switch (e.getStatusCode().value()) {
                case 422 -> "INVALID_REQUEST";
                case 503 -> "CRAWLER_UNAVAILABLE";
                default -> "HTTP_ERROR";
            };
            log.warn("Academic calendar request rejected: year={}, httpStatus={}", year, e.getStatusCode().value());
            return new SyncResult(year, "FAILED", null, failedMonths, reason);
        } catch (org.springframework.web.client.RestClientException e) {
            boolean timeout = false;
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.net.SocketTimeoutException
                        || cause instanceof java.net.http.HttpTimeoutException) timeout = true;
            }
            String reason = timeout ? "REQUEST_TIMEOUT"
                    : e instanceof ResourceAccessException ? "CONNECTION_ERROR" : "INVALID_RESPONSE_OR_CLIENT_ERROR";
            log.warn("Academic calendar request transport failure: year={}, reason={}", year, reason);
            // The crawler may have accepted the request before the connection failed.
            // Never automatically retry: inspect its durable status first.
            return new SyncResult(year, "FAILED", null, failedMonths, reason);
        } catch (RuntimeException e) {
            log.warn("Academic calendar crawl request failed: year={}, error={}", year, e.getClass().getSimpleName());
            return new SyncResult(year, "FAILED", null, failedMonths, "INVALID_RESPONSE_OR_CLIENT_ERROR");
        }
    }
    public record SyncResult(int academicYear, String crawlStatus, String runId,
                             List<String> failedHolidayMonths, String failureReason) {}
}
