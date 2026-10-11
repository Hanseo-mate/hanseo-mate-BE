package hsu.hanseomate.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.fasterxml.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.notification.entity.Notification;
import hsu.hanseomate.domain.notification.repository.NotificationRepository;
import hsu.hanseomate.domain.push.repository.NotificationOutboxRepository;
import hsu.hanseomate.domain.push.service.NotificationService;
import hsu.hanseomate.domain.push.entity.OutboxStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notification-timezone;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    @Test
    void shuttleReminderCreatesBroadcastOutboxAndInboxVisibleWithoutLogin() throws Exception {
        notificationService.enqueueShuttleReservationReminder(
                OffsetDateTime.parse("2026-10-14T18:00:00+09:00"));

        assertThat(outboxRepository.findAll()).singleElement().satisfies(outbox -> {
            assertThat(outbox.getTargetUserId()).isNull();
            assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(outbox.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 14, 9, 0));
        });
        var payload = new ObjectMapper().readTree(outboxRepository.findAll().get(0).getPayload());
        assertThat(payload.path("title").asText()).isEqualTo("[셔틀 예매 알림]");
        assertThat(payload.path("body").asText()).isEqualTo("약 10분 뒤인 오후 6시에 셔틀 예매가 시작됩니다.");
        assertThat(payload.path("data").path("type").asText()).isEqualTo("shuttle_reservation");
        assertThat(payload.path("data").path("route").asText()).isEqualTo("/notifications");
        assertThat(payload.path("data").path("startsAt").asText()).isEqualTo("2026-10-14T18:00:00+09:00");
        assertThat(payload.path("data").path("minutesBefore").asInt()).isEqualTo(10);
        assertThat(notificationRepository.findAll()).singleElement().satisfies(notification -> {
            assertThat(notification.getTargetUserId()).isNull();
            assertThat(notification.getPayloadData()).isEqualTo(payload.path("data").toString());
        });

        mockMvc.perform(get("/api/v1/notifications").param("installationId", "shuttle-guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("[셔틀 예매 알림]"))
                .andExpect(jsonPath("$[0].isRead").value(false));
        mockMvc.perform(get("/api/v1/notifications/unread-count").param("installationId", "shuttle-guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void returnsKoreanOffsetWithoutShiftingStoredTimeAndPreservesNewestFirstOrder()
            throws Exception {
        LocalDateTime older = LocalDateTime.of(2026, 9, 15, 23, 55);
        LocalDateTime newer = LocalDateTime.of(2026, 9, 16, 0, 5, 0, 123456000);
        jdbcTemplate.update("""
                INSERT INTO notifications (title, body, created_at)
                VALUES (?, ?, ?)
                """, "이전 알림", "이전 알림 내용", older);
        jdbcTemplate.update("""
                INSERT INTO notifications (title, body, created_at)
                VALUES (?, ?, ?)
                """, "최근 알림", "최근 알림 내용", newer);

        String response = mockMvc.perform(get("/api/v1/notifications")
                        .param("installationId", "timezone-test-device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("최근 알림"))
                .andExpect(jsonPath("$[0].createdAt")
                        .value("2026-09-16T00:05:00.123456+09:00"))
                .andExpect(jsonPath("$[1].createdAt")
                        .value("2026-09-15T23:55:00+09:00"))
                .andReturn().getResponse().getContentAsString();

        String createdAt = JsonPath.read(response, "$[0].createdAt");
        assertThat(OffsetDateTime.parse(createdAt).toInstant())
                .isEqualTo(Instant.parse("2026-09-15T15:05:00.123456Z"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_at FROM notifications WHERE title = ?",
                LocalDateTime.class,
                "최근 알림"
        )).isEqualTo(newer);
    }

    @Test
    void storesAndReturnsKoreanTimeEvenWhenJvmDefaultIsUtc() throws Exception {
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            ZoneId koreaZone = ZoneId.of("Asia/Seoul");
            LocalDateTime beforeCreate = LocalDateTime.now(koreaZone).minusSeconds(1);

            Notification notification = notificationRepository.saveAndFlush(
                    Notification.builder()
                            .title("한국 시간 확인")
                            .body("한국 시간 확인 내용")
                            .payloadData("{}")
                            .build()
            );

            LocalDateTime afterCreate = LocalDateTime.now(koreaZone).plusSeconds(1);
            assertThat(notification.getCreatedAt()).isBetween(beforeCreate, afterCreate);

            String response = mockMvc.perform(get("/api/v1/notifications")
                            .param("installationId", "korea-time-test-device"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].title").value("한국 시간 확인"))
                    .andReturn().getResponse().getContentAsString();

            String createdAt = JsonPath.read(response, "$[0].createdAt");
            OffsetDateTime responseTime = OffsetDateTime.parse(createdAt);
            assertThat(responseTime.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
            assertThat(responseTime.toLocalDateTime())
                    .isEqualTo(notification.getCreatedAt());
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }
}
