package hsu.hanseomate.domain.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.calendar.entity.CalendarEvent;
import hsu.hanseomate.domain.calendar.repository.CalendarEventRepository;
import hsu.hanseomate.support.AdminMockMvcConfiguration;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminMockMvcConfiguration.class)
class CalendarEventApiIntegrationTest {

    private static final String PUBLIC_PATH = "/api/calendars";
    private static final String ADMIN_PATH = "/api/admin/calendars";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        calendarEventRepository.deleteAll();
    }

    @Test
    void jpaCreatesExpectedCalendarEventColumns() {
        var columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE LOWER(table_name) = 'student_council_calendar_events'
                ORDER BY ordinal_position
                """, String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "start_date", "end_date", "title", "content",
                "created_at", "updated_at"
        );
    }

    @Test
    void publicListIsAvailableWithoutLoginAndReturnsEmptyArray() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createsCalendarEventAndPreservesContent() throws Exception {
        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startDate": "2026-08-10",
                                  "endDate": "2026-08-12",
                                  "title": "  학생회 행사 안내  ",
                                  "content": "행사 내용을 안내합니다. 🎉\\n많은 참여 부탁드립니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.startDate").value("2026-08-10"))
                .andExpect(jsonPath("$.endDate").value("2026-08-12"))
                .andExpect(jsonPath("$.title").value("학생회 행사 안내"))
                .andExpect(jsonPath("$.content")
                        .value("행사 내용을 안내합니다. 🎉\n많은 참여 부탁드립니다."))
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        CalendarEvent saved = calendarEventRepository.findAll().get(0);
        assertThat(saved.getContent())
                .isEqualTo("행사 내용을 안내합니다. 🎉\n많은 참여 부탁드립니다.");
    }

    @Test
    void allowsSameDayAndOverlappingEventsWithoutCountLimit() throws Exception {
        for (int i = 1; i <= 25; i++) {
            mockMvc.perform(post(ADMIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "startDate", "2026-09-01",
                                    "endDate", "2026-09-03",
                                    "title", "중복 일정 " + i,
                                    "content", "서로 겹치는 일정"
                            ))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026-09-02",
                                "2026-09-02",
                                "하루 일정",
                                "시작일과 종료일이 같습니다."
                        )))
                .andExpect(status().isCreated());

        assertThat(calendarEventRepository.count()).isEqualTo(26);
        mockMvc.perform(get(PUBLIC_PATH).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(26));
    }

    @Test
    void publicAndAdminListsReturnEveryEventInDateOrder() throws Exception {
        CalendarEvent later = saveEvent(
                "2026-10-10", "2026-10-11", "나중 일정", "나중 내용"
        );
        CalendarEvent earlierLong = saveEvent(
                "2026-09-01", "2026-09-05", "먼저 시작하는 긴 일정", "긴 일정"
        );
        CalendarEvent earlierShort = saveEvent(
                "2026-09-01", "2026-09-03", "먼저 시작하는 짧은 일정", "짧은 일정"
        );

        mockMvc.perform(get(PUBLIC_PATH).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(earlierShort.getId()))
                .andExpect(jsonPath("$[1].id").value(earlierLong.getId()))
                .andExpect(jsonPath("$[2].id").value(later.getId()));

        mockMvc.perform(get(ADMIN_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(earlierShort.getId()))
                .andExpect(jsonPath("$[1].id").value(earlierLong.getId()))
                .andExpect(jsonPath("$[2].id").value(later.getId()));
    }

    @Test
    void updatesAllCalendarEventFields() throws Exception {
        CalendarEvent event = saveEvent(
                "2026-08-10", "2026-08-11", "수정 전", "수정 전 내용"
        );

        mockMvc.perform(put(ADMIN_PATH + "/{calendarId}", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026-08-20",
                                "2026-08-25",
                                "  수정 후 일정  ",
                                "수정 후 내용 ✅"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId()))
                .andExpect(jsonPath("$.startDate").value("2026-08-20"))
                .andExpect(jsonPath("$.endDate").value("2026-08-25"))
                .andExpect(jsonPath("$.title").value("수정 후 일정"))
                .andExpect(jsonPath("$.content").value("수정 후 내용 ✅"));

        assertThat(calendarEventRepository.count()).isEqualTo(1);
    }

    @Test
    void deletesOnlyRequestedCalendarEvent() throws Exception {
        CalendarEvent deleted = saveEvent(
                "2026-08-10", "2026-08-10", "삭제 대상", "삭제 내용"
        );
        CalendarEvent remaining = saveEvent(
                "2026-08-11", "2026-08-11", "유지 대상", "유지 내용"
        );

        mockMvc.perform(delete(ADMIN_PATH + "/{calendarId}", deleted.getId()))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        assertThat(calendarEventRepository.existsById(deleted.getId())).isFalse();
        assertThat(calendarEventRepository.existsById(remaining.getId())).isTrue();
    }

    @Test
    void rejectsInvalidCalendarEventRequests() throws Exception {
        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "content": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026-08-12",
                                "2026-08-10",
                                "역전 일정",
                                "종료일이 더 빠릅니다."
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("일정 종료일은 시작일보다 빠를 수 없습니다."));

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026/08/10",
                                "2026-08-12",
                                "잘못된 날짜",
                                "날짜 형식 오류"
                        )))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(ADMIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "startDate", "2026-08-10",
                                "endDate", "2026-08-12",
                                "title", "가".repeat(501),
                                "content", "제목 길이 오류"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingOrInvalidCalendarIdReturnsClientError() throws Exception {
        mockMvc.perform(put(ADMIN_PATH + "/{calendarId}", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value(ADMIN_PATH + "/999999"));

        mockMvc.perform(delete(ADMIN_PATH + "/{calendarId}", 999_999L))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(ADMIN_PATH + "/{calendarId}", 0L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void everyAdminCalendarApiRequiresAdminRole() throws Exception {
        CalendarEvent event = saveEvent(
                "2026-08-10", "2026-08-10", "권한 확인", "권한 확인 내용"
        );

        mockMvc.perform(get(ADMIN_PATH).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(ADMIN_PATH)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(ADMIN_PATH + "/{calendarId}", event.getId())
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ADMIN_PATH + "/{calendarId}", event.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(ADMIN_PATH).with(userJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_PATH)
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN_PATH + "/{calendarId}", event.getId())
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ADMIN_PATH + "/{calendarId}", event.getId())
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiContainsOnlyRequestedCalendarOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/calendars'].get").exists())
                .andExpect(jsonPath("$.paths['/api/calendars'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/calendars'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/calendars'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/calendars/{calendarId}'].put"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/calendars/{calendarId}'].delete"
                ).exists())
                .andExpect(jsonPath("$.paths['/api/calendars/{calendarId}']")
                        .doesNotExist());
    }

    private CalendarEvent saveEvent(
            String startDate,
            String endDate,
            String title,
            String content
    ) {
        return calendarEventRepository.saveAndFlush(CalendarEvent.create(
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                title,
                content
        ));
    }

    private String validRequest() throws Exception {
        return request(
                "2026-08-10",
                "2026-08-12",
                "학생회 일정",
                "학생회 일정 내용"
        );
    }

    private String request(
            String startDate,
            String endDate,
            String title,
            String content
    ) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "startDate", startDate,
                "endDate", endDate,
                "title", title,
                "content", content
        ));
    }

    private RequestPostProcessor userJwt() {
        return jwt()
                .jwt(token -> token
                        .subject("calendar-user")
                        .claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
