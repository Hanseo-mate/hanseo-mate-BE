package hsu.hanseomate.domain.personalcalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.personalcalendar.entity.PersonalCalendarEvent;
import hsu.hanseomate.domain.personalcalendar.repository.PersonalCalendarEventRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonalCalendarApiIntegrationTest {

    private static final String PATH = "/api/calendars/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonalCalendarEventRepository personalCalendarEventRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        personalCalendarEventRepository.deleteAll();
    }

    @Test
    void jpaCreatesExpectedPersonalCalendarColumns() {
        var columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE LOWER(table_name) = 'personal_calendar_events'
                ORDER BY ordinal_position
                """, String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "owner_id", "start_date", "end_date", "title",
                "created_at", "updated_at"
        );
    }

    @Test
    void issuedLoginTokenCanCreateAndReadPersonalEvent() throws Exception {
        String loginId = "personal-calendar-login-" + System.nanoTime();
        String password = "test-password";
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "loginId", loginId,
                                "password", password
                        ))))
                .andExpect(status().isCreated());
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "loginId", loginId,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()
        ).path("accessToken").stringValue();

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026-08-10",
                                "2026-08-11",
                                "Issued token event"
                        )))
                .andExpect(status().isCreated());
        mockMvc.perform(get(PATH).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Issued token event"));
    }

    @Test
    void createsListsUpdatesAndDeletesOnlyMyEvents() throws Exception {
        long userId = createUser();
        long laterId = createEvent(
                userId,
                "2026-08-15",
                "2026-08-15",
                "  Later event  "
        );
        long earlierId = createEvent(
                userId,
                "2026-08-10",
                "2026-08-20",
                "Earlier event"
        );

        mockMvc.perform(get(PATH).with(userJwt(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(earlierId))
                .andExpect(jsonPath("$[0].title").value("Earlier event"))
                .andExpect(jsonPath("$[1].id").value(laterId))
                .andExpect(jsonPath("$[1].title").value("Later event"))
                .andExpect(jsonPath("$[0].content").doesNotExist())
                .andExpect(jsonPath("$[0].ownerId").doesNotExist());

        mockMvc.perform(put(PATH + "/{calendarId}", laterId)
                        .with(userJwt(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026-08-11",
                                "2026-08-12",
                                "  Updated event  "
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(laterId))
                .andExpect(jsonPath("$.startDate").value("2026-08-11"))
                .andExpect(jsonPath("$.endDate").value("2026-08-12"))
                .andExpect(jsonPath("$.title").value("Updated event"));

        mockMvc.perform(delete(PATH + "/{calendarId}", earlierId)
                        .with(userJwt(userId)))
                .andExpect(status().isNoContent());

        assertThat(personalCalendarEventRepository.findById(earlierId)).isEmpty();
        assertThat(personalCalendarEventRepository.findById(laterId))
                .get()
                .extracting(PersonalCalendarEvent::getTitle)
                .isEqualTo("Updated event");
    }

    @Test
    void allowsSameDayOverlappingAndDuplicateEvents() throws Exception {
        long userId = createUser();

        for (int index = 0; index < 3; index++) {
            createEvent(userId, "2026-08-10", "2026-08-10", "Same event");
        }
        createEvent(userId, "2026-08-09", "2026-08-12", "Overlapping event");

        mockMvc.perform(get(PATH).with(userJwt(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void isolatesUsersAndHidesOtherUsersEventIds() throws Exception {
        long firstUserId = createUser();
        long secondUserId = createUser();
        long firstEventId = createEvent(
                firstUserId,
                "2026-08-10",
                "2026-08-11",
                "First user event"
        );
        long secondEventId = createEvent(
                secondUserId,
                "2026-08-12",
                "2026-08-13",
                "Second user event"
        );

        mockMvc.perform(get(PATH).with(userJwt(firstUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(firstEventId));
        mockMvc.perform(get(PATH).with(userJwt(secondUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(secondEventId));

        mockMvc.perform(put(PATH + "/{calendarId}", secondEventId)
                        .with(userJwt(firstUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026-09-01",
                                "2026-09-02",
                                "Unauthorized update"
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(delete(PATH + "/{calendarId}", secondEventId)
                        .with(userJwt(firstUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        assertThat(personalCalendarEventRepository.findById(secondEventId)).isPresent();
    }

    @Test
    void validatesPersonalCalendarRequests() throws Exception {
        long userId = createUser();

        mockMvc.perform(post(PATH)
                        .with(userJwt(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "2026-08-12",
                                "2026-08-10",
                                "Invalid range"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(post(PATH)
                        .with(userJwt(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-08-10", "2026-08-10", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(delete(PATH + "/0").with(userJwt(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void requiresValidAuthenticationForEveryPersonalCalendarOperation()
            throws Exception {
        mockMvc.perform(get(PATH).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH)
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-08-10", "2026-08-10", "Event")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(PATH + "/1")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("2026-08-10", "2026-08-10", "Event")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(PATH + "/1").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer invalid-token"
                ))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).with(jwt().jwt(token -> token
                        .subject("not-a-number")
                        .claim("role", "USER"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).with(userJwt(Long.MAX_VALUE)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/calendars").with(anonymous()))
                .andExpect(status().isOk());
    }

    @Test
    void deletingUserAccountCascadesPersonalEvents() throws Exception {
        long userId = createUser();
        long eventId = createEvent(
                userId,
                "2026-08-10",
                "2026-08-11",
                "Event"
        );

        userAccountRepository.deleteById(userId);
        userAccountRepository.flush();

        assertThat(personalCalendarEventRepository.findById(eventId)).isEmpty();
    }

    @Test
    void openApiContainsPersonalCalendarOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/calendars/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/calendars/me'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/calendars/me/{calendarId}'].put"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/calendars/me/{calendarId}'].delete"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.PersonalCalendarEventRequest.properties.content"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.PersonalCalendarEventResponse.properties.content"
                ).doesNotExist());
    }

    private long createUser() {
        return userAccountRepository.saveAndFlush(UserAccount.create(
                "personal-calendar-user-" + System.nanoTime(),
                "test-password-hash"
        )).getId();
    }

    private long createEvent(
            long userId,
            String startDate,
            String endDate,
            String title
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(PATH)
                        .with(userJwt(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(startDate, endDate, title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("id")
                .asLong();
    }

    private String request(String startDate, String endDate, String title) {
        return objectMapper.writeValueAsString(Map.of(
                "startDate", startDate,
                "endDate", endDate,
                "title", title
        ));
    }

    private RequestPostProcessor userJwt(long userId) {
        return jwt().jwt(token -> token
                .subject(Long.toString(userId))
                .claim("role", "USER"));
    }
}
