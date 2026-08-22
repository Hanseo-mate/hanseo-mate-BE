package hsu.hanseomate.domain.systemnotice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.notification.entity.Notification;
import hsu.hanseomate.domain.notification.repository.NotificationReadRepository;
import hsu.hanseomate.domain.notification.repository.NotificationRepository;
import hsu.hanseomate.domain.push.entity.NotificationOutbox;
import hsu.hanseomate.domain.push.repository.NotificationOutboxRepository;
import hsu.hanseomate.domain.systemnotice.entity.SystemNotice;
import hsu.hanseomate.domain.systemnotice.repository.SystemNoticeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemNoticeApiIntegrationTest {

    private static final String PUBLIC_PATH = "/api/system-notices";
    private static final String ADMIN_PATH = "/api/admin/system-notices";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SystemNoticeRepository systemNoticeRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationReadRepository notificationReadRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        notificationReadRepository.deleteAll();
        notificationRepository.deleteAll();
        notificationOutboxRepository.deleteAll();
        systemNoticeRepository.deleteAll();
    }

    @Test
    void jpaCreatesExactlyTheRequiredColumns() {
        var columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE LOWER(table_name) = 'system_notices'
                ORDER BY ordinal_position
                """, String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "title", "content", "created_at", "updated_at"
        );
    }

    @Test
    void anonymousUserReceivesEmptyListWhenNoNoticesExist() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void adminCreatesNoticeWithOnlyTitleAndContent() throws Exception {
        String content = "첫 번째 줄\n두 번째 줄 🚀";

        mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("  서비스 점검 안내  ", content)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("서비스 점검 안내"))
                .andExpect(jsonPath("$.content").value(content))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.author").doesNotExist())
                .andExpect(jsonPath("$.viewCount").doesNotExist());

        SystemNotice saved = systemNoticeRepository.findAll().get(0);
        assertThat(saved.getTitle()).isEqualTo("서비스 점검 안내");
        assertThat(saved.getContent()).isEqualTo(content);
    }

    @Test
    void creatingSystemNoticeEnqueuesGlobalPushAndInboxNotification() throws Exception {
        mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("서비스 점검 안내", "오늘 23시에 점검합니다.")))
                .andExpect(status().isCreated());

        SystemNotice notice = systemNoticeRepository.findAll().get(0);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.get(0);
        assertThat(notification.getTitle()).isEqualTo("[시스템 공지] 서비스 점검 안내");
        assertThat(notification.getBody()).isEqualTo("새로운 시스템 공지가 등록되었습니다.");
        assertThat(notification.getPayloadData())
                .contains("\"type\":\"system_notice\"")
                .contains("\"route\":\"/system-notices\"")
                .contains("\"entityId\":\"" + notice.getId() + "\"");

        List<NotificationOutbox> outboxes = notificationOutboxRepository.findAll();
        assertThat(outboxes).hasSize(1);
        NotificationOutbox outbox = outboxes.get(0);
        assertThat(outbox.getPayload())
                .contains("\"title\":\"[시스템 공지] 서비스 점검 안내\"")
                .contains("\"body\":\"새로운 시스템 공지가 등록되었습니다.\"")
                .contains("\"type\":\"system_notice\"")
                .contains("\"route\":\"/system-notices\"")
                .contains("\"entityId\":\"" + notice.getId() + "\"");
    }

    @Test
    void updatingAndDeletingSystemNoticeDoNotCreateAnotherNotification() throws Exception {
        SystemNotice notice = saveNotice("기존 공지", "기존 내용");

        mockMvc.perform(put(ADMIN_PATH + "/{noticeId}", notice.getId())
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("수정 공지", "수정 내용")))
                .andExpect(status().isOk());

        mockMvc.perform(delete(ADMIN_PATH + "/{noticeId}", notice.getId())
                        .with(adminJwt()))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.count()).isZero();
        assertThat(notificationOutboxRepository.count()).isZero();
    }

    @Test
    void publicListReturnsEveryNoticeNewestFirstWithFullContent() throws Exception {
        SystemNotice first = saveNotice("첫 공지", "첫 공지 전체 내용");
        SystemNotice second = saveNotice("두 번째 공지", "두 번째 공지 전체 내용");

        mockMvc.perform(get(PUBLIC_PATH).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]", aMapWithSize(5)))
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[0].title").value("두 번째 공지"))
                .andExpect(jsonPath("$[0].content").value("두 번째 공지 전체 내용"))
                .andExpect(jsonPath("$[1].id").value(first.getId()))
                .andExpect(jsonPath("$[1].content").value("첫 공지 전체 내용"));
    }

    @Test
    void adminListReturnsEveryNoticeNewestFirst() throws Exception {
        SystemNotice first = saveNotice("첫 공지", "첫 내용");
        SystemNotice second = saveNotice("두 번째 공지", "두 번째 내용");

        mockMvc.perform(get(ADMIN_PATH).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[0].content").value("두 번째 내용"))
                .andExpect(jsonPath("$[1].id").value(first.getId()));
    }

    @Test
    void adminUpdatesEntireNoticeAndPreservesCreatedAt() throws Exception {
        SystemNotice notice = saveNotice("기존 제목", "기존 내용");
        LocalDateTime createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM system_notices WHERE id = ?",
                LocalDateTime.class,
                notice.getId()
        );

        mockMvc.perform(put(ADMIN_PATH + "/{noticeId}", notice.getId())
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("  변경 제목  ", "변경 내용")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notice.getId()))
                .andExpect(jsonPath("$.title").value("변경 제목"))
                .andExpect(jsonPath("$.content").value("변경 내용"))
                .andExpect(jsonPath("$.updatedAt").exists());

        SystemNotice updated = systemNoticeRepository.findById(notice.getId())
                .orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void adminDeletesNotice() throws Exception {
        SystemNotice notice = saveNotice("삭제 공지", "삭제할 내용");

        mockMvc.perform(delete(ADMIN_PATH + "/{noticeId}", notice.getId())
                        .with(adminJwt()))
                .andExpect(status().isNoContent());

        assertThat(systemNoticeRepository.existsById(notice.getId())).isFalse();
    }

    @Test
    void validatesTitleContentAndNoticeId() throws Exception {
        mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("   ", "내용")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title: 제목은 필수입니다."));

        mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("content: 내용은 필수입니다."));

        mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("가".repeat(501), "내용")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("title: 제목은 500자 이하여야 합니다."));

        mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", "가".repeat(100_001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("content: 내용은 100,000자 이하여야 합니다."));

        mockMvc.perform(put(ADMIN_PATH + "/0")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", "내용")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete(ADMIN_PATH + "/abc").with(adminJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonAndMissingNoticesUseCommonErrors() throws Exception {
        mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(put(ADMIN_PATH + "/999999")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", "내용")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("시스템 공지를 찾을 수 없습니다. noticeId=999999"));

        mockMvc.perform(delete(ADMIN_PATH + "/999999").with(adminJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void storesAndReturnsLongContentWithEmoji() throws Exception {
        String content = "시스템 장문 공지 🚀\n".repeat(5_000);

        mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("장문 공지", content)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(content));

        String stored = jdbcTemplate.queryForObject(
                "SELECT content FROM system_notices",
                String.class
        );
        assertThat(stored).isEqualTo(content).contains("🚀");
    }

    @Test
    void everyAdminEndpointRequiresAdminRole() throws Exception {
        SystemNotice notice = saveNotice("권한 테스트", "내용");

        mockMvc.perform(get(ADMIN_PATH).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(ADMIN_PATH)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", "내용")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(ADMIN_PATH + "/{noticeId}", notice.getId())
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", "내용")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ADMIN_PATH + "/{noticeId}", notice.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(ADMIN_PATH).with(userJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_PATH)
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", "내용")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN_PATH + "/{noticeId}", notice.getId())
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("제목", "내용")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ADMIN_PATH + "/{noticeId}", notice.getId())
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiContainsSystemNoticeContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/system-notices'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/system-notices'].get"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/system-notices'].post"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/system-notices/{noticeId}'].put"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/system-notices/{noticeId}'].delete"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.SystemNoticeRequest.properties",
                        aMapWithSize(2)
                ))
                .andExpect(jsonPath(
                        "$.components.schemas.SystemNoticeRequest.properties.title"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.SystemNoticeRequest.properties.content"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.SystemNoticeRequest.properties.author"
                ).doesNotExist());
    }

    private SystemNotice saveNotice(String title, String content) {
        return systemNoticeRepository.saveAndFlush(SystemNotice.create(title, content));
    }

    private String request(String title, String content) {
        return objectMapper.writeValueAsString(Map.of(
                "title", title,
                "content", content
        ));
    }

    private RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(token -> token.subject("1").claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private RequestPostProcessor userJwt() {
        return jwt()
                .jwt(token -> token.subject("1").claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
