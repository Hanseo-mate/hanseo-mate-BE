package hsu.hanseomate.domain.home;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.courseimport.dto.CourseImportResponse;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import hsu.hanseomate.domain.courseimport.service.CourseImportService;
import hsu.hanseomate.domain.homeposter.entity.HomePoster;
import hsu.hanseomate.domain.homeposter.repository.HomePosterRepository;
import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeRepository;
import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import hsu.hanseomate.global.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(HomeApiIntegrationTest.FixedClockConfiguration.class)
class HomeApiIntegrationTest {

    private static final String FIXTURE =
            "fixtures/course-import/course-search-major-2026-1.json";
    private static final Long CURRENT_USER_ID = 101L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseImportService courseImportService;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private TimetableCourseRepository timetableCourseRepository;

    @Autowired
    private HomePosterRepository homePosterRepository;

    @Autowired
    private StudentCouncilNoticeRepository studentCouncilNoticeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
        jdbcTemplate.update(
                """
                        INSERT INTO user_accounts (
                            id, login_id, password_hash, role, created_at, updated_at
                        ) VALUES
                            (?, ?, ?, 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                            (?, ?, ?, 'USER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                CURRENT_USER_ID,
                "home-test-user",
                "test-password-hash",
                202L,
                "home-other-test-user",
                "test-password-hash"
        );
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void returnsPostersAndCategoryTopNoticesWithoutLogin() throws Exception {
        HomePoster firstPoster = homePosterRepository.saveAndFlush(HomePoster.create(
                "https://cdn.test/poster-1.png",
                "https://www.hanseo.ac.kr/event/1"
        ));
        HomePoster secondPoster = homePosterRepository.saveAndFlush(HomePoster.create(
                "https://cdn.test/poster-2.png",
                null
        ));

        insertStudentCouncilNotice("조회수 낮은 학생회 공지", 2L);
        insertStudentCouncilNotice("조회수 높은 학생회 공지", 20L);
        insertCrawledNotice(
                "academic",
                "academic-low",
                "조회수 낮은 학사 공지",
                LocalDate.of(2026, 5, 1),
                3L
        );
        insertCrawledNotice(
                "academic",
                "academic-high",
                "조회수 높은 학사 공지",
                LocalDate.of(2026, 4, 1),
                30L
        );
        insertCrawledNotice(
                "scholarship",
                "scholarship-high",
                "조회수 높은 장학 공지",
                LocalDate.of(2026, 3, 1),
                40L
        );

        mockMvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(false))
                .andExpect(jsonPath("$.posterImageUrls.length()").value(2))
                .andExpect(jsonPath("$.posterImageUrls[0]")
                        .value("https://cdn.test/poster-1.png"))
                .andExpect(jsonPath("$.posterImageUrls[1]")
                        .value("https://cdn.test/poster-2.png"))
                .andExpect(jsonPath("$.posters.length()").value(2))
                .andExpect(jsonPath("$.posters[0].id").value(firstPoster.getId()))
                .andExpect(jsonPath("$.posters[0].imageUrl")
                        .value("https://cdn.test/poster-1.png"))
                .andExpect(jsonPath("$.posters[0].linkUrl")
                        .value("https://www.hanseo.ac.kr/event/1"))
                .andExpect(jsonPath("$.posters[1].id").value(secondPoster.getId()))
                .andExpect(jsonPath("$.posters[1].imageUrl")
                        .value("https://cdn.test/poster-2.png"))
                .andExpect(jsonPath("$.posters[1].linkUrl").value(nullValue()))
                .andExpect(jsonPath("$.todayCourses").isEmpty())
                .andExpect(jsonPath("$.popularNotices.length()").value(3))
                .andExpect(jsonPath("$.popularNotices[0].noticeType")
                        .value("STUDENT_COUNCIL"))
                .andExpect(jsonPath("$.popularNotices[0].title")
                        .value("조회수 높은 학생회 공지"))
                .andExpect(jsonPath("$.popularNotices[1].noticeType")
                        .value("ACADEMIC"))
                .andExpect(jsonPath("$.popularNotices[1].title")
                        .value("조회수 높은 학사 공지"))
                .andExpect(jsonPath("$.popularNotices[2].noticeType")
                        .value("SCHOLARSHIP"))
                .andExpect(jsonPath("$.popularNotices[2].title")
                        .value("조회수 높은 장학 공지"));

        assertViewCount("student_council_notices", "조회수 높은 학생회 공지", 20L);
        assertViewCount("notices", "조회수 높은 학사 공지", 30L);
        assertViewCount("notices", "조회수 높은 장학 공지", 40L);
    }

    @Test
    void returnsOnlyCurrentUsersCoursesForKoreanCurrentWeekday() throws Exception {
        importMajorFixture();
        CourseOffering mondayCourse = offeringByCode("003000");
        CourseOffering thursdayCourse = offeringByCode("004000");

        Timetable currentUserTimetable = timetableRepository.saveAndFlush(
                Timetable.create(CURRENT_USER_ID, 2026, 1)
        );
        timetableCourseRepository.saveAndFlush(
                TimetableCourse.create(currentUserTimetable, mondayCourse)
        );
        timetableCourseRepository.saveAndFlush(
                TimetableCourse.create(currentUserTimetable, thursdayCourse)
        );

        Timetable otherUserTimetable = timetableRepository.saveAndFlush(
                Timetable.create(202L, 2026, 1)
        );
        timetableCourseRepository.saveAndFlush(
                TimetableCourse.create(otherUserTimetable, thursdayCourse)
        );

        mockMvc.perform(get("/api/home")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + validAccessToken(CURRENT_USER_ID.toString())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.todayCourses.length()").value(1))
                .andExpect(jsonPath("$.todayCourses[0].startTime").value("12:00"))
                .andExpect(jsonPath("$.todayCourses[0].endTime").value("13:00"))
                .andExpect(jsonPath("$.todayCourses[0].courseName")
                        .value("델타프로젝트"))
                .andExpect(jsonPath("$.todayCourses[0].buildingName")
                        .value("디자인관"))
                .andExpect(jsonPath("$.todayCourses[0].roomNumber").value("401"));
    }

    @Test
    void preservesKnownTimeWhenScheduleCrossesUnsupportedPeriodBoundary()
            throws Exception {
        String payload = fixturePayload().replace(
                "\"periods\": [6, 7]",
                "\"periods\": [23, 24]"
        );
        importMajorFixture(payload);
        CourseOffering thursdayCourse = offeringByCode("004000");
        Timetable timetable = timetableRepository.saveAndFlush(
                Timetable.create(CURRENT_USER_ID, 2026, 1)
        );
        timetableCourseRepository.saveAndFlush(
                TimetableCourse.create(timetable, thursdayCourse)
        );

        mockMvc.perform(get("/api/home")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + validAccessToken(CURRENT_USER_ID.toString())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayCourses.length()").value(2))
                .andExpect(jsonPath("$.todayCourses[0].courseName")
                        .value("델타프로젝트"))
                .andExpect(jsonPath("$.todayCourses[0].startTime").value("22:10"))
                .andExpect(jsonPath("$.todayCourses[0].endTime").value("22:55"))
                .andExpect(jsonPath("$.todayCourses[1].courseName")
                        .value("델타프로젝트"))
                .andExpect(jsonPath("$.todayCourses[1].startTime")
                        .value(nullValue()))
                .andExpect(jsonPath("$.todayCourses[1].endTime")
                        .value(nullValue()));
    }

    @Test
    void returnsLoggedInWithEmptyCoursesWhenCurrentTermTimetableDoesNotExist()
            throws Exception {
        mockMvc.perform(get("/api/home")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + validAccessToken(CURRENT_USER_ID.toString())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedIn").value(true))
                .andExpect(jsonPath("$.todayCourses").isEmpty());
    }

    @Test
    void returnsNullPostersAndThreeNullNoticeTitlesWhenDataDoesNotExist()
            throws Exception {
        mockMvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterImageUrls").value(nullValue()))
                .andExpect(jsonPath("$.posters").value(nullValue()))
                .andExpect(jsonPath("$.todayCourses").isEmpty())
                .andExpect(jsonPath("$.popularNotices.length()").value(3))
                .andExpect(jsonPath("$.popularNotices[0].title").value(nullValue()))
                .andExpect(jsonPath("$.popularNotices[1].title").value(nullValue()))
                .andExpect(jsonPath("$.popularNotices[2].title").value(nullValue()));
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/home")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/home"));
    }

    @Test
    void rejectsExpiredBearerToken() throws Exception {
        Instant now = Instant.now();
        String expiredToken = signedAccessToken(
                CURRENT_USER_ID.toString(),
                now.minusSeconds(7_200),
                now.minusSeconds(3_600)
        );

        mockMvc.perform(get("/api/home")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + expiredToken
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsSignedTokenWithNonNumericSubject() throws Exception {
        mockMvc.perform(get("/api/home")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + validAccessToken("not-a-user-id")
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/home"));
    }

    @Test
    void exposesHomeEndpointInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/home'].get").exists())
                .andExpect(jsonPath("$.paths['/api/home'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/home'].get.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/home'].get.responses['401']")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.HomePageResponse.properties.posters"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.HomePosterItemResponse.properties.linkUrl"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.HomePosterItemResponse.properties"
                                + ".linkUrl.type"
                ).value(hasItem("null")))
                .andExpect(jsonPath(
                        "$.components.schemas.HomePageResponse.properties.posters.type"
                ).value(hasItem("null")));
    }

    private void importMajorFixture() throws Exception {
        importMajorFixture(fixturePayload());
    }

    private void importMajorFixture(String payload) {
        CourseImportResponse response = courseImportService.importCourses(
                objectMapper.readValue(payload, TimetableParseResultRequest.class)
        );
        org.assertj.core.api.Assertions.assertThat(response.storageStatus())
                .isEqualTo(StorageStatus.STORED);
    }

    private String fixturePayload() throws Exception {
        return new ClassPathResource(FIXTURE)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private CourseOffering offeringByCode(String courseCode) {
        return courseOfferingRepository.findAll().stream()
                .filter(offering -> courseCode.equals(offering.getCourseCode()))
                .findFirst()
                .orElseThrow();
    }

    private void insertStudentCouncilNotice(String title, long viewCount) {
        StudentCouncilNotice notice = studentCouncilNoticeRepository.saveAndFlush(
                StudentCouncilNotice.create(title, "학생회", "공지 내용")
        );
        jdbcTemplate.update(
                "UPDATE student_council_notices SET view_count = ? WHERE id = ?",
                viewCount,
                notice.getId()
        );
    }

    private void insertCrawledNotice(
            String noticeType,
            String originNoticeId,
            String title,
            LocalDate postDate,
            long viewCount
    ) {
        jdbcTemplate.update("""
                INSERT INTO notices (
                    notice_type,
                    origin_notice_id,
                    title,
                    source_url,
                    content_html,
                    author,
                    post_date,
                    is_hot,
                    view_count
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                noticeType,
                originNoticeId,
                title,
                "https://www.hanseo.ac.kr/detail/" + originNoticeId,
                "<p>content</p>",
                "관리자",
                Date.valueOf(postDate),
                false,
                viewCount
        );
    }

    private void assertViewCount(String table, String title, long expected) {
        Long actual = jdbcTemplate.queryForObject(
                "SELECT view_count FROM " + table + " WHERE title = ?",
                Long.class,
                title
        );
        org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected);
    }

    private String validAccessToken(String subject) {
        Instant now = Instant.now();
        return signedAccessToken(subject, now.minusSeconds(60), now.plusSeconds(3_600));
    }

    private String signedAccessToken(
            String subject,
            Instant issuedAt,
            Instant expiresAt
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(subject)
                .claim("role", "USER")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            truncate("notice_files");
            truncate("notices");
            truncate("student_council_notices");
            truncate("home_posters");
            truncate("timetable_courses");
            truncate("timetables");
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

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                    Instant.parse("2026-05-06T15:30:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}
