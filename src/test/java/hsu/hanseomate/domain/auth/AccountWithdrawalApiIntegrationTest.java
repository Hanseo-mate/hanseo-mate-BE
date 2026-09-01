package hsu.hanseomate.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.club.entity.Club;
import hsu.hanseomate.domain.club.entity.ClubLike;
import hsu.hanseomate.domain.club.entity.ClubReview;
import hsu.hanseomate.domain.club.repository.ClubLikeRepository;
import hsu.hanseomate.domain.club.repository.ClubRepository;
import hsu.hanseomate.domain.club.repository.ClubReviewRepository;
import hsu.hanseomate.domain.club.type.ClubCategory;
import hsu.hanseomate.domain.club.type.ClubReviewOption;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.courseimport.dto.CourseImportResponse;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import hsu.hanseomate.domain.courseimport.service.CourseImportService;
import hsu.hanseomate.domain.personalcalendar.entity.PersonalCalendarEvent;
import hsu.hanseomate.domain.personalcalendar.repository.PersonalCalendarEventRepository;
import hsu.hanseomate.domain.push.entity.NotificationOutbox;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.entity.PushTicket;
import hsu.hanseomate.domain.push.repository.NotificationOutboxRepository;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.domain.push.repository.PushTicketRepository;
import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountWithdrawalApiIntegrationTest {

    private static final String WITHDRAWAL_PATH = "/api/auth/me";
    private static final String COURSE_FIXTURE =
            "fixtures/course-import/course-search-major-2026-1.json";
    private static final String PASSWORD = "test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PersonalCalendarEventRepository personalCalendarEventRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubLikeRepository clubLikeRepository;

    @Autowired
    private ClubReviewRepository clubReviewRepository;

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private TimetableCourseRepository timetableCourseRepository;

    @Autowired
    private PushDeviceRepository pushDeviceRepository;

    @Autowired
    private PushTicketRepository pushTicketRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private CourseImportService courseImportService;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void withdrawalPermanentlyDeletesOnlyCurrentUsersAccountAndOwnedData()
            throws Exception {
        AuthSession current = signupAndLogin("withdrawal-user");
        AuthSession other = signupAndLogin("remaining-user");
        UserAccount currentUser = userAccountRepository.findById(current.userId()).orElseThrow();
        UserAccount otherUser = userAccountRepository.findById(other.userId()).orElseThrow();

        CourseOffering sharedOffering = importSharedOffering();
        Club sharedClub = clubRepository.saveAndFlush(
                Club.create("공용 동아리", ClubCategory.ACADEMIC)
        );
        NotificationOutbox sharedOutbox = saveCompletedOutbox(
                "{\"title\":\"공용 공지\"}"
        );
        OwnedData currentData = seedOwnedData(
                currentUser, sharedClub, sharedOffering, sharedOutbox, "current", 2026
        );
        OwnedData otherData = seedOwnedData(
                otherUser, sharedClub, sharedOffering, sharedOutbox, "other", 2027
        );

        withdraw(current.accessToken(), PASSWORD)
                .andExpect(status().isNoContent());
        entityManager.clear();

        assertThat(userAccountRepository.existsById(current.userId())).isFalse();
        assertOwnedDataDeleted(current.userId(), currentData);

        assertThat(userAccountRepository.existsById(other.userId())).isTrue();
        assertOwnedDataRemains(other.userId(), otherData);

        assertThat(clubRepository.existsById(sharedClub.getId())).isTrue();
        assertThat(courseOfferingRepository.existsById(sharedOffering.getId())).isTrue();
        assertThat(notificationOutboxRepository.existsById(sharedOutbox.getId())).isTrue();
    }

    @Test
    void sameLoginIdCanSignupAgainWithNewIdentityAndNoRestoredData() throws Exception {
        AuthSession original = signupAndLogin("rejoin-user");
        UserAccount originalUser = userAccountRepository.findById(original.userId()).orElseThrow();
        CourseOffering offering = importSharedOffering();
        Club club = clubRepository.saveAndFlush(
                Club.create("재가입 검증 동아리", ClubCategory.HOBBY)
        );
        NotificationOutbox outbox = saveCompletedOutbox(
                "{\"title\":\"재가입 검증\"}"
        );
        seedOwnedData(originalUser, club, offering, outbox, "original", 2026);

        withdraw(original.accessToken(), PASSWORD)
                .andExpect(status().isNoContent());

        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("rejoin-user", "new-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loginId").value("rejoin-user"))
                .andReturn();
        JsonNode signupBody = responseBody(signupResult);
        long newUserId = signupBody.path("userId").asLong();
        String newAccessToken = signupBody.path("accessToken").stringValue();

        assertThat(newUserId).isNotEqualTo(original.userId());
        assertThat(count("personal_calendar_events", "owner_id", newUserId)).isZero();
        assertThat(count("club_likes", "liker_id", newUserId)).isZero();
        assertThat(count("club_reviews", "reviewer_id", newUserId)).isZero();
        assertThat(count("timetables", "owner_id", newUserId)).isZero();
        assertThat(count("push_devices", "user_id", newUserId)).isZero();

        mockMvc.perform(get(WITHDRAWAL_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(newUserId))
                .andExpect(jsonPath("$.clubReviews").isEmpty())
                .andExpect(jsonPath("$.likedClubs").isEmpty());
    }

    @Test
    void wrongPasswordReturnsUnauthorizedAndKeepsAccountAndAllOwnedData()
            throws Exception {
        AuthSession current = signupAndLogin("wrong-password-user");
        UserAccount currentUser = userAccountRepository.findById(current.userId()).orElseThrow();
        CourseOffering offering = importSharedOffering();
        Club club = clubRepository.saveAndFlush(
                Club.create("비밀번호 실패 동아리", ClubCategory.VOLUNTEER)
        );
        NotificationOutbox outbox = saveCompletedOutbox(
                "{\"title\":\"비밀번호 실패\"}"
        );
        OwnedData ownedData = seedOwnedData(
                currentUser, club, offering, outbox, "wrong-password", 2026
        );

        withdraw(current.accessToken(), "incorrect-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(WITHDRAWAL_PATH));
        entityManager.clear();

        assertThat(userAccountRepository.existsById(current.userId())).isTrue();
        assertOwnedDataRemains(current.userId(), ownedData);
    }

    @Test
    void blankPasswordReturnsBadRequestAndKeepsAccount() throws Exception {
        AuthSession current = signupAndLogin("blank-password-user");

        withdraw(current.accessToken(), " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(WITHDRAWAL_PATH));

        assertThat(userAccountRepository.existsById(current.userId())).isTrue();
    }

    @Test
    void withdrawalRequiresValidBearerToken() throws Exception {
        mockMvc.perform(delete(WITHDRAWAL_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawalBody(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(delete(WITHDRAWAL_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawalBody(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void oldAccessTokenCannotUseUserApisAfterWithdrawal() throws Exception {
        AuthSession current = signupAndLogin("expired-account-user");

        withdraw(current.accessToken(), PASSWORD)
                .andExpect(status().isNoContent());

        mockMvc.perform(get(WITHDRAWAL_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(current.accessToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/timetables")
                        .header(HttpHeaders.AUTHORIZATION, bearer(current.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "year", 2026,
                                "semester", 1
                        ))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/push-tokens")
                        .header(HttpHeaders.AUTHORIZATION, bearer(current.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "expoPushToken", "ExponentPushToken[old-account]",
                                "projectId", "project-id",
                                "platform", "android",
                                "installationId", "old-account-installation",
                                "appVersion", "1.0.0"
                        ))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", current.refreshToken()
                        ))))
                .andExpect(status().isUnauthorized());

        assertThat(count("timetables", "owner_id", current.userId())).isZero();
        assertThat(count("push_devices", "user_id", current.userId())).isZero();
        assertThat(count("refresh_tokens", "user_id", current.userId())).isZero();
    }

    @Test
    void openApiDocumentsWithdrawalEndpointAndRequest() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/me'].delete").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/me'].delete.requestBody.required"
                ).value(true))
                .andExpect(jsonPath(
                        "$.paths['/api/auth/me'].delete.requestBody.content"
                                + "['application/json'].schema['$ref']"
                ).value("#/components/schemas/WithdrawalRequest"))
                .andExpect(jsonPath(
                        "$.paths['/api/auth/me'].delete.responses['204']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/me'].delete.responses['400']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/me'].delete.responses['401']"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.WithdrawalRequest.properties.password"
                ).exists());
    }

    private OwnedData seedOwnedData(
            UserAccount user,
            Club club,
            CourseOffering offering,
            NotificationOutbox outbox,
            String key,
            int timetableYear
    ) {
        PersonalCalendarEvent personalEvent = personalCalendarEventRepository.saveAndFlush(
                PersonalCalendarEvent.create(
                        user,
                        LocalDate.of(2026, 8, 18),
                        LocalDate.of(2026, 8, 18),
                        key + " 개인 일정"
                )
        );
        ClubLike like = clubLikeRepository.saveAndFlush(ClubLike.create(club, user));
        ClubReview review = clubReviewRepository.saveAndFlush(ClubReview.create(
                club,
                user,
                new LinkedHashSet<>(java.util.List.of(
                        ClubReviewOption.SOCIALIZING,
                        ClubReviewOption.BUILD_RESUME
                ))
        ));
        Timetable timetable = timetableRepository.saveAndFlush(
                Timetable.create(user.getId(), timetableYear, 1)
        );
        TimetableCourse timetableCourse = timetableCourseRepository.saveAndFlush(
                TimetableCourse.create(timetable, offering)
        );
        PushDevice pushDevice = pushDeviceRepository.saveAndFlush(PushDevice.create(
                user.getId(),
                key + "-installation",
                "ExponentPushToken[" + key + "]",
                "android",
                "project-id",
                "1.0.0"
        ));
        PushTicket pushTicket = pushTicketRepository.saveAndFlush(PushTicket.create(
                key + "-ticket",
                outbox.getId(),
                pushDevice.getId()
        ));
        return new OwnedData(
                personalEvent.getId(),
                like.getId(),
                review.getId(),
                timetable.getId(),
                timetableCourse.getId(),
                pushDevice.getId(),
                pushTicket.getId()
        );
    }

    private CourseOffering importSharedOffering() throws Exception {
        String payload = new ClassPathResource(COURSE_FIXTURE)
                .getContentAsString(StandardCharsets.UTF_8);
        CourseImportResponse response = courseImportService.importCourses(
                objectMapper.readValue(payload, TimetableParseResultRequest.class)
        );
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.STORED);
        return courseOfferingRepository.findAll().stream().findFirst().orElseThrow();
    }

    private NotificationOutbox saveCompletedOutbox(String payload) {
        NotificationOutbox outbox = NotificationOutbox.create(payload);
        outbox.markSent();
        return notificationOutboxRepository.saveAndFlush(outbox);
    }

    private void assertOwnedDataDeleted(long userId, OwnedData data) {
        assertThat(count("personal_calendar_events", "owner_id", userId)).isZero();
        assertThat(count("club_likes", "liker_id", userId)).isZero();
        assertThat(count("club_reviews", "reviewer_id", userId)).isZero();
        assertThat(count("club_review_selections", "club_review_id", data.reviewId()))
                .isZero();
        assertThat(count("timetables", "owner_id", userId)).isZero();
        assertThat(count("timetable_courses", "timetable_id", data.timetableId())).isZero();
        assertThat(count("push_devices", "user_id", userId)).isZero();
        assertThat(count("push_tickets", "push_device_id", data.pushDeviceId())).isZero();
        assertThat(count("refresh_tokens", "user_id", userId)).isZero();
    }

    private void assertOwnedDataRemains(long userId, OwnedData data) {
        assertThat(count("personal_calendar_events", "owner_id", userId)).isOne();
        assertThat(count("club_likes", "liker_id", userId)).isOne();
        assertThat(count("club_reviews", "reviewer_id", userId)).isOne();
        assertThat(count("club_review_selections", "club_review_id", data.reviewId()))
                .isEqualTo(2);
        assertThat(count("timetables", "owner_id", userId)).isOne();
        assertThat(count("timetable_courses", "timetable_id", data.timetableId())).isOne();
        assertThat(count("push_devices", "user_id", userId)).isOne();
        assertThat(count("push_tickets", "push_device_id", data.pushDeviceId())).isOne();
        assertThat(count("refresh_tokens", "user_id", userId)).isEqualTo(2);
    }

    private org.springframework.test.web.servlet.ResultActions withdraw(
            String accessToken,
            String password
    ) throws Exception {
        return mockMvc.perform(delete(WITHDRAWAL_PATH)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(withdrawalBody(password)));
    }

    private AuthSession signupAndLogin(String loginId) throws Exception {
        String body = credentials(loginId, PASSWORD);
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = responseBody(signupResult).path("userId").asLong();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = responseBody(loginResult).path("accessToken").stringValue();
        String refreshToken = responseBody(loginResult).path("refreshToken").stringValue();
        return new AuthSession(userId, accessToken, refreshToken);
    }

    private String credentials(String loginId, String password) {
        return objectMapper.writeValueAsString(Map.of(
                "loginId", loginId,
                "password", password
        ));
    }

    private String withdrawalBody(String password) {
        return objectMapper.writeValueAsString(Map.of("password", password));
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private long count(String table, String column, long value) {
        Long result = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class,
                value
        );
        return result == null ? 0L : result;
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            truncate("push_tickets");
            truncate("push_devices");
            truncate("notification_outbox");
            truncate("club_review_selections");
            truncate("club_reviews");
            truncate("club_likes");
            truncate("clubs");
            truncate("personal_calendar_events");
            truncate("timetable_courses");
            truncate("timetables");
            truncate("refresh_tokens");
            truncate("user_accounts");
            truncate("course_import_issues");
            truncate("course_schedules");
            truncate("course_source_cells");
            truncate("offering_allowed_grades");
            truncate("offering_eligible_departments");
            truncate("offering_general_education");
            truncate("course_offerings");
            truncate("semester_academic_units");
            truncate("semester_general_category_nodes");
            truncate("course_import_histories");
            truncate("classrooms");
            truncate("courses");
            truncate("academic_units");
            truncate("semesters");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private void truncate(String table) {
        jdbcTemplate.execute("TRUNCATE TABLE " + table);
    }

    private record AuthSession(
            long userId,
            String accessToken,
            String refreshToken
    ) {
    }

    private record OwnedData(
            long personalEventId,
            long likeId,
            long reviewId,
            long timetableId,
            long timetableCourseId,
            long pushDeviceId,
            long pushTicketId
    ) {
    }
}
