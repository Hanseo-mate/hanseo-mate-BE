package hsu.hanseomate.domain.academiccalendar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import hsu.hanseomate.domain.academiccalendar.client.AcademicCalendarCrawlerClient;
import hsu.hanseomate.domain.academiccalendar.service.AcademicCalendarSyncService;
import hsu.hanseomate.domain.academiccalendar.service.HolidaySyncService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class AcademicCalendarHttpTest {
    static class Provider implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<String> request = new AtomicReference<>();
        final AtomicReference<String> contentType = new AtomicReference<>();
        final CountDownLatch release = new CountDownLatch(1);
        Provider(int status, String body, boolean stall) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r); thread.setDaemon(true); return thread;
            }));
            server.createContext("/academic-calendar-crawl/run", exchange -> {
                calls.incrementAndGet();
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                if (stall) {
                    try { release.await(20, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                try {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } finally { exchange.close(); }
            });
            server.start();
        }
        AcademicCalendarCrawlerClient client() {
            return new AcademicCalendarCrawlerClient(RestClient.builder(),
                    "http://127.0.0.1:" + server.getAddress().getPort());
        }
        AcademicCalendarSyncService service() {
            return new AcademicCalendarSyncService(client(), mock(HolidaySyncService.class),
                    Clock.fixed(Instant.parse("2026-12-31T19:00:00Z"), ZoneOffset.UTC));
        }
        public void close() { release.countDown(); server.stop(0); }
    }

    @Test void verifiesRequestAndRecordsAcceptanceRatherThanCompletion() throws Exception {
        try (var provider = new Provider(202, "{\"status\":\"starting\",\"run_id\":\"test-run\"}", false)) {
            var result = provider.service().sync();
            assertThat(result.crawlStatus()).isEqualTo("ACCEPTED");
            assertThat(result.runId()).isEqualTo("test-run");
            assertThat(result.failureReason()).isNull();
            var json = new tools.jackson.databind.ObjectMapper().readTree(provider.request.get());
            assertThat(json.path("academic_year").asInt()).isEqualTo(2027);
            assertThat(json.path("semesters").toString()).isEqualTo("[1,2]");
            assertThat(json.path("mode").asString()).isEqualTo("background");
            assertThat(provider.contentType.get()).startsWith("application/json");
            assertThat(provider.calls).hasValue(1);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201})
    void rejectsSuccessStatusOtherThan202(int status) throws Exception {
        try (var provider = new Provider(status, "{\"status\":\"starting\",\"run_id\":\"id\"}", false)) {
            assertThat(provider.service().sync().failureReason()).isEqualTo("INVALID_RESPONSE_OR_CLIENT_ERROR");
            assertThat(provider.calls).hasValue(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "{\"status\":\"completed\",\"run_id\":\"id\"}",
            "{\"status\":\"starting\",\"run_id\":\"\"}",
            "{\"status\":\"starting\",\"run_id\":\"   \"}",
            "{\"status\":\"starting\",\"run_id\":17}",
            "{\"status\":\"starting\",\"run_id\":null}", "not-json", ""
    })
    void rejectsMalformedOrInvalidReceipts(String body) throws Exception {
        try (var provider = new Provider(202, body, false)) {
            var result = provider.service().sync();
            assertThat(result.crawlStatus()).isEqualTo("FAILED");
            assertThat(result.runId()).isNull();
            assertThat(result.failureReason()).isEqualTo("INVALID_RESPONSE_OR_CLIENT_ERROR");
        }
    }

    @ParameterizedTest
    @CsvSource({"409,ALREADY_RUNNING,NONE", "422,FAILED,INVALID_REQUEST",
            "503,FAILED,CRAWLER_UNAVAILABLE", "500,FAILED,HTTP_ERROR"})
    void distinguishesProviderErrorsWithoutRetry(int httpStatus, String status, String reason) throws Exception {
        try (var provider = new Provider(httpStatus, "{\"detail\":\"provider failure\"}", false)) {
            var result = provider.service().sync();
            assertThat(result.crawlStatus()).isEqualTo(status);
            assertThat(result.failureReason()).isEqualTo(reason.equals("NONE") ? null : reason);
            assertThat(result.runId()).isNull();
            assertThat(provider.calls).hasValue(1);
        }
    }

    @Test void actual15SecondReadTimeoutDoesNotRetryAnUncertainAcceptance() throws Exception {
        try (var provider = new Provider(202, "{\"status\":\"starting\",\"run_id\":\"id\"}", true)) {
            long started = System.nanoTime();
            var result = provider.service().sync();
            assertThat(result.crawlStatus()).isEqualTo("FAILED");
            assertThat(result.failureReason()).isEqualTo("REQUEST_TIMEOUT");
            assertThat(Duration.ofNanos(System.nanoTime() - started).toSeconds()).isBetween(14L, 19L);
            assertThat(provider.calls).hasValue(1);
        }
    }

    @Test void connectionFailureIsReportedWithoutRetry() throws Exception {
        var provider = new Provider(202, "{}", false);
        var service = provider.service();
        provider.close();
        assertThat(service.sync().failureReason()).isEqualTo("CONNECTION_ERROR");
    }
}
