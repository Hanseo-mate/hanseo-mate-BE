package hsu.hanseomate.domain.cafeteria.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sun.net.httpserver.HttpServer;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.service.CafeteriaScheduler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class CafeteriaCrawlerClientTest {
    private static final String ACCEPTED =
            "{\"status\":\"starting\",\"run_id\":\"accepted-run\",\"results\":{}}";
    private final Queue<Request> requests = new ConcurrentLinkedQueue<>();
    private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final JsonMapper json = JsonMapper.builder().build();
    private HttpServer server;
    private CafeteriaCrawlerClient client;
    private CafeteriaScheduler scheduler;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new Request(
                    exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Reply reply = replies.poll();
            if (reply == null) reply = new Reply(200, ACCEPTED);
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new CafeteriaCrawlerClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort());
        scheduler = new CafeteriaScheduler(client);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void everyWeekdaySlotSendsOneIdenticalPendingRequest() {
        CronExpression cron = CronExpression.parse(CafeteriaScheduler.CRON);
        ZonedDateTime cursor = ZonedDateTime.parse("2026-10-05T00:00:00+09:00[Asia/Seoul]");
        for (int i = 0; i < 45; i++) {
            cursor = cron.next(cursor);
            scheduler.triggerPendingCrawl();
        }
        assertThat(requests).hasSize(45);
        requests.forEach(this::assertPendingRequest);
        assertThat(cursor.getHour()).isEqualTo(17);
        assertThat(cursor.getDayOfMonth()).isEqualTo(9);
    }

    @ParameterizedTest
    @ValueSource(ints = {409, 400, 500, 503})
    void failureEndsThisSlotAndNextSlotStillRequests(int status, CapturedOutput output) {
        replies.add(new Reply(status, "{\"detail\":\"test error\"}"));
        assertThatCode(scheduler::triggerPendingCrawl).doesNotThrowAnyException();
        assertThat(requests).hasSize(1); // No immediate retry loop.
        assertThat(output).contains(status == 409
                ? "이미 크롤링이 실행 중" : "다음 정기 시각에 다시 요청");
        CronExpression cron = CronExpression.parse(CafeteriaScheduler.CRON);
        ZonedDateTime current = ZonedDateTime.parse("2026-10-06T01:00:00+09:00[Asia/Seoul]");
        assertThat(cron.next(current)).isEqualTo(current.plusHours(2));
        scheduler.triggerPendingCrawl();
        assertThat(requests).hasSize(2);
        requests.forEach(this::assertPendingRequest);
    }

    @Test
    void connectionFailureDoesNotEscapeScheduler() {
        server.stop(0);
        assertThatCode(scheduler::triggerPendingCrawl).doesNotThrowAnyException();
    }

    @Test
    void acceptanceDoesNotClaimDatabaseCompletion(CapturedOutput output) {
        var response = client.triggerPendingCrawl();
        assertThat(response.status()).isEqualTo("starting");
        assertThat(response.runId()).isEqualTo("accepted-run");
        assertThat(output).contains("요청 접수", "status=starting", "run_id=accepted-run");
        assertThat(output).doesNotContain("저장 완료", "수집 완료");
    }

    @Test
    void skippedResponseDoesNotPreventNextDaysRequest() {
        replies.add(new Reply(200, """
                {"status":"completed","run_id":"skipped-run",
                 "results":{"MAIN_STUDENT":{"status":"skipped"}}}
                """));
        scheduler.triggerPendingCrawl();
        new CafeteriaScheduler(client).triggerPendingCrawl();
        assertThat(requests).hasSize(2);
        requests.forEach(this::assertPendingRequest);
    }

    @Test
    void manualForceUsesFalseAndOptionalRestaurantArray() {
        client.triggerAllCrawl();
        client.triggerCrawl(List.of(RestaurantType.MAIN_STUDENT), false);
        assertThat(json.readTree(requests.remove().body())).isEqualTo(json.readTree("""
                {"mode":"background","only_pending":false}
                """));
        assertThat(json.readTree(requests.remove().body())).isEqualTo(json.readTree("""
                {"mode":"background","only_pending":false,
                 "restaurant_types":["MAIN_STUDENT"]}
                """));
    }

    @Test
    void statusReadsNestedMenusAndIgnoresCompatibilityRetryFields() {
        replies.add(new Reply(200, """
                {"status":"partial_failed","run_id":"run-1",
                 "started_at":"2026-10-05T16:00:00Z","finished_at":"2026-10-05T16:00:05Z",
                 "retry_count":0,"max_retry_count":0,"next_retry_at":null,
                 "results":{
                   "MAIN_STUDENT":{"status":"unchanged","updated":false,
                     "saved_daily_menus":0,"menus":[
                       {"menuDate":"2026-10-06","restaurantType":"MAIN_STUDENT",
                        "mealSections":[]}]},
                   "TAEAN_STUDENT":{"status":"failed","error":"HTTP 503"}}}
                """));
        var status = client.checkStatus();
        assertThat(status.runId()).isEqualTo("run-1");
        assertThat(status.startedAt()).isEqualTo(OffsetDateTime.parse("2026-10-05T16:00:00Z"));
        assertThat(status.results().get(RestaurantType.MAIN_STUDENT).menus())
                .singleElement().satisfies(menu ->
                        assertThat(menu.menuDate()).isEqualTo(LocalDate.of(2026, 10, 6)));
        assertThat(status.results().get(RestaurantType.TAEAN_STUDENT).error()).isEqualTo("HTTP 503");
        assertThat(requests.remove().path()).isEqualTo("/cafeteria-crawl/status");
    }

    @Test
    void dailyStatusPreservesKoreanDatesAndUtcTimesWithoutGatingNextRequest() {
        replies.add(new Reply(200, """
                {"business_date":"2026-10-06","pending_restaurant_types":["TAEAN_STUDENT"],
                 "results":{
                   "MAIN_STUDENT":{"completed_today":true,"status":"unchanged",
                     "business_date":"2026-10-06","run_id":"today-run",
                     "last_attempt_at":"2026-10-05T16:00:00Z","last_success_date":"2026-10-06",
                     "last_success_at":"2026-10-05T16:00:05Z","error":null},
                   "TAEAN_STUDENT":{"completed_today":false,"status":"running",
                     "business_date":"2026-10-06","run_id":"old-process-run",
                     "last_attempt_at":"2026-10-05T16:00:00Z","last_success_date":"2026-10-05",
                     "last_success_at":"2026-10-04T16:00:05Z","error":null}}}
                """));
        var status = client.checkDailyStatus();
        assertThat(status.businessDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(status.pendingRestaurantTypes()).containsExactly(RestaurantType.TAEAN_STUDENT);
        var completed = status.results().get(RestaurantType.MAIN_STUDENT);
        assertThat(completed.completedToday()).isTrue();
        assertThat(completed.lastSuccessAt()).isEqualTo(
                OffsetDateTime.parse("2026-10-05T16:00:05Z"));
        var pending = status.results().get(RestaurantType.TAEAN_STUDENT);
        assertThat(pending.completedToday()).isFalse();
        assertThat(pending.lastSuccessDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(requests.remove().path()).isEqualTo("/cafeteria-crawl/daily-status");
        scheduler.triggerPendingCrawl();
        assertPendingRequest(requests.remove());
    }

    private void assertPendingRequest(Request request) {
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/cafeteria-crawl/run");
        assertThat(request.contentType()).startsWith("application/json");
        assertThat(json.readTree(request.body())).isEqualTo(json.readTree("""
                {"mode":"background","only_pending":true}
                """));
    }

    private record Request(String method, String path, String contentType, String body) {}
    private record Reply(int status, String body) {}
}
