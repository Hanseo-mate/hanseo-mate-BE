package hsu.hanseomate.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hsu.hanseomate.domain.notification.entity.Notification;
import hsu.hanseomate.domain.notification.repository.NotificationRepository;
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
