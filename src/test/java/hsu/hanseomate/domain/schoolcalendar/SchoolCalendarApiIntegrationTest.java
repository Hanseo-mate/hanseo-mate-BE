package hsu.hanseomate.domain.schoolcalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.schoolcalendar.entity.SchoolCalendarEvent;
import hsu.hanseomate.domain.schoolcalendar.repository.SchoolCalendarEventRepository;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class SchoolCalendarApiIntegrationTest {

    private static final String PUBLIC_PATH = "/api/calendars/school";
    private static final String ADMIN_PATH = "/api/admin/school-calendars";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SchoolCalendarEventRepository schoolCalendarEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        schoolCalendarEventRepository.deleteAll();
    }

    @Test
    void jpaCreatesExpectedSchoolCalendarColumns() {
        var columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE LOWER(table_name) = 'school_calendar_events'
                ORDER BY ordinal_position
                """, String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "start_date", "end_date", "title",
                "created_at", "updated_at"
        );
    }

    @Test
    void publicListIsAvailableWithoutLogin() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void adminCreatesListsUpdatesAndDeletesSchoolEvent() throws Exception {
        MvcResult createResult = mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026-08-24",
                                "2026-08-24",
                                "  Second semester begins  "
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Second semester begins"))
                .andExpect(jsonPath("$.content").doesNotExist())
                .andReturn();
        long calendarId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()
        ).path("id").asLong();

        mockMvc.perform(get(PUBLIC_PATH).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(calendarId));
        mockMvc.perform(get(ADMIN_PATH).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(put(ADMIN_PATH + "/{calendarId}", calendarId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026-08-25",
                                "2026-08-26",
                                "Updated school event"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(calendarId))
                .andExpect(jsonPath("$.startDate").value("2026-08-25"))
                .andExpect(jsonPath("$.endDate").value("2026-08-26"))
                .andExpect(jsonPath("$.title").value("Updated school event"));

        mockMvc.perform(delete(ADMIN_PATH + "/{calendarId}", calendarId)
                        .with(adminJwt()))
                .andExpect(status().isNoContent());
        assertThat(schoolCalendarEventRepository.findById(calendarId)).isEmpty();
    }

    @Test
    void allowsOverlappingAndDuplicateSchoolEventsInDateOrder() throws Exception {
        saveEvent("2026-08-10", "2026-08-12", "Same event");
        saveEvent("2026-08-10", "2026-08-12", "Same event");
        saveEvent("2026-08-09", "2026-08-15", "Overlapping event");

        mockMvc.perform(get(PUBLIC_PATH).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Overlapping event"));
    }

    @Test
    void validatesRequestsAndMissingSchoolEvents() throws Exception {
        mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026-08-12",
                                "2026-08-10",
                                "Invalid range"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(post(ADMIN_PATH)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-08-10", "2026-08-10", "   ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(ADMIN_PATH + "/999999")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-08-10", "2026-08-10", "Event")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(ADMIN_PATH + "/0").with(adminJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAdminRoleForEverySchoolCalendarMutation() throws Exception {
        SchoolCalendarEvent event = saveEvent(
                "2026-08-10",
                "2026-08-10",
                "School event"
        );

        mockMvc.perform(get(ADMIN_PATH).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(ADMIN_PATH)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-08-10", "2026-08-10", "Event")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(ADMIN_PATH + "/{calendarId}", event.getId())
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-08-10", "2026-08-10", "Event")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ADMIN_PATH + "/{calendarId}", event.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(ADMIN_PATH).with(userJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_PATH)
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-08-10", "2026-08-10", "Event")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN_PATH + "/{calendarId}", event.getId())
                        .with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-08-10", "2026-08-10", "Event")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ADMIN_PATH + "/{calendarId}", event.getId())
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiContainsSchoolCalendarOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/calendars/school'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/school-calendars'].get"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/school-calendars'].post"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/school-calendars/{calendarId}'].put"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/school-calendars/{calendarId}'].delete"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.SchoolCalendarEventRequest.properties.content"
                ).doesNotExist());
    }

    private SchoolCalendarEvent saveEvent(
            String startDate,
            String endDate,
            String title
    ) {
        return schoolCalendarEventRepository.saveAndFlush(SchoolCalendarEvent.create(
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                title
        ));
    }

    private String request(String startDate, String endDate, String title) {
        return objectMapper.writeValueAsString(Map.of(
                "startDate", startDate,
                "endDate", endDate,
                "title", title
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
