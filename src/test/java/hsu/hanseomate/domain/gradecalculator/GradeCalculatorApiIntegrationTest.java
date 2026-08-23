package hsu.hanseomate.domain.gradecalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.courseimport.dto.CourseImportResponse;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import hsu.hanseomate.domain.courseimport.service.CourseImportService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GradeCalculatorApiIntegrationTest {

    private static final String CALCULATIONS_PATH = "/api/grade-calculations";
    private static final String TIMETABLE_COURSES_PATH =
            CALCULATIONS_PATH + "/timetable-courses";
    private static final String TIMETABLE_PATH = "/api/timetables";
    private static final String FIXTURE =
            "fixtures/course-import/course-search-major-2026-1.json";
    private static final String ALPHA_CODE = "003000";
    private static final String CHARLIE_CODE = "001000";
    private static final String OTHER_CODE = "005000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseImportService courseImportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void gradeScaleIsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get(CALCULATIONS_PATH + "/grades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maximumGpa").value(4.5))
                .andExpect(jsonPath("$.grades.length()").value(10))
                .andExpect(jsonPath("$.grades[0].grade").value("A+"))
                .andExpect(jsonPath("$.grades[0].gradePoint").value(4.5))
                .andExpect(jsonPath("$.grades[0].includedInGpa").value(true))
                .andExpect(jsonPath("$.grades[0].creditEarned").value(true))
                .andExpect(jsonPath("$.grades[8].grade").value("P"))
                .andExpect(jsonPath("$.grades[8].gradePoint").value(nullValue()))
                .andExpect(jsonPath("$.grades[8].includedInGpa").value(false))
                .andExpect(jsonPath("$.grades[8].creditEarned").value(true))
                .andExpect(jsonPath("$.grades[9].grade").value("F"))
                .andExpect(jsonPath("$.grades[9].gradePoint").value(0.0))
                .andExpect(jsonPath("$.grades[9].includedInGpa").value(true))
                .andExpect(jsonPath("$.grades[9].creditEarned").value(false));
    }

    @Test
    void calculatesOfficialExampleWithoutAuthentication() throws Exception {
        String request = """
                {
                  "courses": [
                    {
                      "courseName": "자료구조",
                      "credit": 3,
                      "expectedGrade": "A+",
                      "curriculumType": "MAJOR"
                    },
                    {
                      "courseName": "모바일프로그래밍",
                      "credit": 2,
                      "expectedGrade": "B",
                      "curriculumType": "MAJOR"
                    },
                    {
                      "courseName": "봉사활동",
                      "credit": 1,
                      "expectedGrade": "P",
                      "curriculumType": "GENERAL_EDUCATION"
                    }
                  ]
                }
                """;

        mockMvc.perform(post(CALCULATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maximumGpa").value(4.5))
                .andExpect(jsonPath("$.appliedCredits").value(6))
                .andExpect(jsonPath("$.gpaCredits").value(5))
                .andExpect(jsonPath("$.earnedCredits").value(6))
                .andExpect(jsonPath("$.expectedGpa").value(3.90))
                .andExpect(jsonPath("$.ungradedCourseCount").value(0))
                .andExpect(jsonPath("$.status").value("COMPLETE"));
    }

    @Test
    void rejectsUnsupportedGradeAndZeroCredit() throws Exception {
        mockMvc.perform(post(CALCULATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courses": [{
                                    "courseName": "잘못된 성적",
                                    "credit": 3,
                                    "expectedGrade": "A++"
                                  }]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(CALCULATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courses": [{
                                    "courseName": "0학점 과목",
                                    "credit": 0,
                                    "expectedGrade": "A"
                                  }]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void timetableCourseImportRequiresAuthentication() throws Exception {
        mockMvc.perform(get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.path").value(TIMETABLE_COURSES_PATH));
    }

    @Test
    void returnsOnlyAuthenticatedUsersTimetableCoursesForRequestedTerm()
            throws Exception {
        importMajorFixture();
        String ownerToken = registerAndLogin("grade-owner");
        String otherOwnerToken = registerAndLogin("other-grade-owner");
        UUID alphaId = offeringIdByCode(ALPHA_CODE);
        UUID charlieId = offeringIdByCode(CHARLIE_CODE);
        UUID otherId = offeringIdByCode(OTHER_CODE);

        long ownerTimetableId = createTimetable(ownerToken, 2026, 1);
        long alphaTimetableCourseId = addCourse(ownerToken, ownerTimetableId, alphaId);
        long charlieTimetableCourseId = addCourse(ownerToken, ownerTimetableId, charlieId);
        long otherTimetableId = createTimetable(otherOwnerToken, 2026, 1);
        long otherTimetableCourseId = addCourse(
                otherOwnerToken,
                otherTimetableId,
                otherId
        );

        performAuthenticated(ownerToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(ownerTimetableId))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.semester").value(1))
                .andExpect(jsonPath("$.courses.length()").value(2))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(alphaTimetableCourseId))
                .andExpect(jsonPath("$.courses[0].courseId").value(alphaId.toString()))
                .andExpect(jsonPath("$.courses[0].courseName").value("알파개론"))
                .andExpect(jsonPath("$.courses[0].credit").value(1.0))
                .andExpect(jsonPath("$.courses[0].curriculumType").value("MAJOR"))
                .andExpect(jsonPath("$.courses[1].timetableCourseId")
                        .value(charlieTimetableCourseId))
                .andExpect(jsonPath("$.courses[1].courseId").value(charlieId.toString()))
                .andExpect(jsonPath("$.courses[1].courseName").value("찰리실습"))
                .andExpect(jsonPath("$.courses[1].credit").value(2.0))
                .andExpect(jsonPath("$.courses[1].curriculumType").value("MAJOR"));

        performAuthenticated(otherOwnerToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(otherTimetableId))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(otherTimetableCourseId))
                .andExpect(jsonPath("$.courses[0].courseId").value(otherId.toString()))
                .andExpect(jsonPath("$.courses[0].courseName").value("기타세미나"))
                .andExpect(jsonPath("$.courses[0].credit").value(5.0))
                .andExpect(jsonPath("$.courses[0].curriculumType").value("MAJOR"));
    }

    @Test
    void missingRequestedTimetableTermReturnsNotFound() throws Exception {
        String accessToken = registerAndLogin("grade-missing-term");

        performAuthenticated(accessToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("TIMETABLE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("시간표를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path").value(TIMETABLE_COURSES_PATH));
    }

    private String registerAndLogin(String loginId) throws Exception {
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
        return responseBody(loginResult).path("accessToken").stringValue();
    }

    private long createTimetable(String token, int year, int semester) throws Exception {
        MvcResult result = performAuthenticated(token, post(TIMETABLE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "year", year,
                                "semester", semester
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return responseBody(result).path("timetableId").asLong();
    }

    private long addCourse(String token, long timetableId, UUID courseId)
            throws Exception {
        MvcResult result = performAuthenticated(token, post(
                        TIMETABLE_PATH + "/courses/{timetableId}",
                        timetableId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseId", courseId
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return responseBody(result).path("timetableCourseId").asLong();
    }

    private ResultActions performAuthenticated(
            String token,
            MockHttpServletRequestBuilder requestBuilder
    ) throws Exception {
        return mockMvc.perform(requestBuilder.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + token
        ));
    }

    private void importMajorFixture() throws Exception {
        String payload = new ClassPathResource(FIXTURE)
                .getContentAsString(StandardCharsets.UTF_8);
        CourseImportResponse response = courseImportService.importCourses(
                objectMapper.readValue(payload, TimetableParseResultRequest.class)
        );
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.STORED);
        assertThat(response.databaseChanged()).isTrue();
        assertThat(response.offeringCount()).isEqualTo(5);
    }

    private UUID offeringIdByCode(String courseCode) {
        return jdbcTemplate.queryForObject(
                """
                select offering.id
                from course_offerings offering
                join courses course on course.id = offering.course_id
                where offering.active = true and course.course_code = ?
                limit 1
                """,
                UUID.class,
                courseCode
        );
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
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
}
