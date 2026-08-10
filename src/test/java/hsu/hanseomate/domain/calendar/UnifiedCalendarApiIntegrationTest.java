package hsu.hanseomate.domain.calendar;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.calendar.entity.CalendarEvent;
import hsu.hanseomate.domain.calendar.repository.CalendarEventRepository;
import hsu.hanseomate.domain.personalcalendar.entity.PersonalCalendarEvent;
import hsu.hanseomate.domain.personalcalendar.repository.PersonalCalendarEventRepository;
import hsu.hanseomate.domain.schoolcalendar.entity.SchoolCalendarEvent;
import hsu.hanseomate.domain.schoolcalendar.repository.SchoolCalendarEventRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UnifiedCalendarApiIntegrationTest {

    private static final String PATH = "/api/calendars/all";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    @Autowired
    private SchoolCalendarEventRepository schoolCalendarEventRepository;

    @Autowired
    private PersonalCalendarEventRepository personalCalendarEventRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void cleanUp() {
        personalCalendarEventRepository.deleteAll();
        schoolCalendarEventRepository.deleteAll();
        calendarEventRepository.deleteAll();
    }

    @Test
    void anonymousUserReceivesOnlySchoolAndStudentCouncilEvents() throws Exception {
        saveSchoolEvent("2026-08-10", "School event");
        saveStudentCouncilEvent("2026-08-11", "Student council event");
        UserAccount user = createUser();
        savePersonalEvent(user, "2026-08-09", "Personal event");

        mockMvc.perform(get(PATH).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].calendarType").value("SCHOOL"))
                .andExpect(jsonPath("$[0].title").value("School event"))
                .andExpect(jsonPath("$[1].calendarType").value("STUDENT_COUNCIL"))
                .andExpect(jsonPath("$[1].title").value("Student council event"));
    }

    @Test
    void loggedInUserReceivesOnlyTheirPersonalEventsWithPublicEvents() throws Exception {
        AuthSession authSession = registerAndLogin();
        UserAccount currentUser = userAccountRepository.findById(authSession.userId())
                .orElseThrow();
        UserAccount otherUser = createUser();
        saveSchoolEvent("2026-08-10", "School event");
        saveStudentCouncilEvent("2026-08-10", "Student council event");
        savePersonalEvent(currentUser, "2026-08-10", "My event");
        savePersonalEvent(otherUser, "2026-08-09", "Other event");

        mockMvc.perform(get(PATH).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + authSession.accessToken()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].calendarType").value("SCHOOL"))
                .andExpect(jsonPath("$[1].calendarType").value("STUDENT_COUNCIL"))
                .andExpect(jsonPath("$[2].calendarType").value("PERSONAL"))
                .andExpect(jsonPath("$[2].title").value("My event"))
                .andExpect(jsonPath("$[?(@.title == 'Other event')]").isEmpty());
    }

    @Test
    void emptyCalendarReturnsEmptyArray() throws Exception {
        mockMvc.perform(get(PATH).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void invalidOrNonNumericTokenIsUnauthorized() throws Exception {
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
    }

    @Test
    void openApiContainsUnifiedCalendarOperationAndType() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/calendars/all'].get").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.UnifiedCalendarEventResponse.properties.calendarType"
                ).exists());
    }

    private void saveSchoolEvent(String date, String title) {
        schoolCalendarEventRepository.saveAndFlush(SchoolCalendarEvent.create(
                LocalDate.parse(date),
                LocalDate.parse(date),
                title
        ));
    }

    private void saveStudentCouncilEvent(String date, String title) {
        calendarEventRepository.saveAndFlush(CalendarEvent.create(
                LocalDate.parse(date),
                LocalDate.parse(date),
                title
        ));
    }

    private void savePersonalEvent(UserAccount owner, String date, String title) {
        personalCalendarEventRepository.saveAndFlush(PersonalCalendarEvent.create(
                owner,
                LocalDate.parse(date),
                LocalDate.parse(date),
                title
        ));
    }

    private UserAccount createUser() {
        return userAccountRepository.saveAndFlush(UserAccount.create(
                "unified-calendar-user-" + System.nanoTime(),
                "test-password-hash"
        ));
    }

    private AuthSession registerAndLogin() throws Exception {
        String loginId = "unified-calendar-login-" + System.nanoTime();
        String password = "test-password";
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "loginId", loginId,
                                "password", password
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = objectMapper.readTree(
                signupResult.getResponse().getContentAsString()
        ).path("userId").asLong();
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
        return new AuthSession(userId, accessToken);
    }

    private RequestPostProcessor userJwt(long userId) {
        return jwt().jwt(token -> token
                .subject(Long.toString(userId))
                .claim("role", "USER"));
    }

    private record AuthSession(long userId, String accessToken) {
    }
}
