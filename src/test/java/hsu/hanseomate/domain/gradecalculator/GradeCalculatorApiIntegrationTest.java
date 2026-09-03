package hsu.hanseomate.domain.gradecalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private static final String OVERVIEW_PATH = CALCULATIONS_PATH + "/overview";
    private static final String TIMETABLE_COURSES_PATH =
            CALCULATIONS_PATH + "/timetable-courses";
    private static final String TIMETABLE_PATH = "/api/timetables";
    private static final String FIXTURE =
            "fixtures/course-import/course-search-major-2026-1.json";
    private static final String FIXTURE_IMPORT_ID = "course-search-major-2026-1";
    private static final String FIXTURE_HASH =
            "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String ALPHA_CODE = "003000";
    private static final String CHARLIE_CODE = "001000";
    private static final String BETA_CODE = "002000";

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
    void allGradeCalculatorApisRequireAuthentication() throws Exception {
        assertAuthenticationRequired(get(CALCULATIONS_PATH + "/grades"));
        assertAuthenticationRequired(get(OVERVIEW_PATH));
        assertAuthenticationRequired(post(CALCULATIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courses\":[]}"));
        assertAuthenticationRequired(get(TIMETABLE_COURSES_PATH)
                .param("year", "2026")
                .param("semester", "1"));
        assertAuthenticationRequired(patch(TIMETABLE_COURSES_PATH + "/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedGrade\":\"A+\"}"));
        assertAuthenticationRequired(post(TIMETABLE_COURSES_PATH + "/import")
                .param("year", "2026")
                .param("semester", "1"));
    }

    @Test
    void authenticatedUserCanReadGradeScaleAndUseLegacyCalculation()
            throws Exception {
        String accessToken = registerAndLogin("grade-calculation-user");

        performAuthenticated(accessToken, get(CALCULATIONS_PATH + "/grades"))
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

        performAuthenticated(accessToken, post(CALCULATIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
                                """))
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
    void authenticatedLegacyCalculationStillValidatesGradeAndCredit()
            throws Exception {
        String accessToken = registerAndLogin("grade-validation-user");

        performAuthenticated(accessToken, post(CALCULATIONS_PATH)
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

        performAuthenticated(accessToken, post(CALCULATIONS_PATH)
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
    void atLeastOneEditableFieldMustBePresentWhenUpdatingCourse() throws Exception {
        importMajorFixture(2026, 1);
        String accessToken = registerAndLogin("grade-missing-field-user");
        long timetableId = createTimetable(accessToken, 2026, 1);
        long timetableCourseId = addCourse(
                accessToken,
                timetableId,
                offeringIdByTermAndCode(2026, 1, ALPHA_CODE)
        );

        performAuthenticated(accessToken, patch(
                        TIMETABLE_COURSES_PATH + "/{timetableCourseId}",
                        timetableCourseId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("courseName, credit, expectedGrade 중 하나 이상의 필드가 필요합니다."));
    }

    @Test
    void customizedCoursePersistsAndImportRestoresCanonicalValuesButKeepsGrade()
            throws Exception {
        importMajorFixture(2026, 1);
        String accessToken = registerAndLogin("grade-customize-import-user");
        long timetableId = createTimetable(accessToken, 2026, 1);
        long timetableCourseId = addCourse(
                accessToken,
                timetableId,
                offeringIdByTermAndCode(2026, 1, ALPHA_CODE)
        );

        updateGrade(accessToken, timetableCourseId, "\"A+\"")
                .andExpect(status().isOk());

        updateTimetableCourse(
                accessToken,
                timetableCourseId,
                "{\"courseName\":\"  사용자 지정 과목  \"}"
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].courseName")
                        .value("사용자 지정 과목"))
                .andExpect(jsonPath("$.courses[0].credit").value(1.0))
                .andExpect(jsonPath("$.courses[0].expectedGrade").value("A+"));

        updateTimetableCourse(accessToken, timetableCourseId, "{\"credit\":2.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].courseName")
                        .value("사용자 지정 과목"))
                .andExpect(jsonPath("$.courses[0].credit").value(2.5))
                .andExpect(jsonPath("$.courses[0].expectedGrade").value("A+"))
                .andExpect(jsonPath("$.termSummary.totalCredits").value(2.5))
                .andExpect(jsonPath("$.termSummary.gpaCredits").value(2.5))
                .andExpect(jsonPath("$.termSummary.earnedCredits").value(2.5))
                .andExpect(jsonPath("$.termSummary.averageGpa").value(4.50))
                .andExpect(jsonPath("$.cumulativeSummary.totalCredits").value(2.5))
                .andExpect(jsonPath("$.cumulativeSummary.averageGpa").value(4.50));

        performAuthenticated(accessToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].courseName")
                        .value("사용자 지정 과목"))
                .andExpect(jsonPath("$.courses[0].credit").value(2.5))
                .andExpect(jsonPath("$.courses[0].expectedGrade").value("A+"));

        performAuthenticated(accessToken, get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].courseName").value("알파개론"))
                .andExpect(jsonPath("$.courses[0].credit").value(1.0))
                .andExpect(jsonPath("$.gradeSummary.termSummary.totalCredits")
                        .value(2.5));

        performAuthenticated(accessToken, post(
                        TIMETABLE_COURSES_PATH + "/import"
                )
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(timetableId))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(timetableCourseId))
                .andExpect(jsonPath("$.courses[0].courseName").value("알파개론"))
                .andExpect(jsonPath("$.courses[0].credit").value(1.0))
                .andExpect(jsonPath("$.courses[0].expectedGrade").value("A+"))
                .andExpect(jsonPath("$.termSummary.totalCredits").value(1.0))
                .andExpect(jsonPath("$.termSummary.gpaCredits").value(1.0))
                .andExpect(jsonPath("$.termSummary.earnedCredits").value(1.0))
                .andExpect(jsonPath("$.termSummary.averageGpa").value(4.50))
                .andExpect(jsonPath("$.cumulativeSummary.totalCredits").value(1.0))
                .andExpect(jsonPath("$.cumulativeSummary.averageGpa").value(4.50));

        performAuthenticated(accessToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].courseName").value("알파개론"))
                .andExpect(jsonPath("$.courses[0].credit").value(1.0))
                .andExpect(jsonPath("$.courses[0].expectedGrade").value("A+"));
    }

    @Test
    void manuallyAddedTimetableCourseParticipatesInGradeCalculationAndSurvivesImport()
            throws Exception {
        String accessToken = registerAndLogin("manual-timetable-grade-user");
        long timetableId = createTimetable(accessToken, 2026, 1);
        MvcResult createResult = performAuthenticated(accessToken, post(
                        TIMETABLE_PATH + "/{timetableId}/custom-courses",
                        timetableId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseName": "개인 프로젝트",
                                  "credit": 2.5,
                                  "dayOfWeek": "THURSDAY",
                                  "startTime": "15:00",
                                  "endTime": "16:30"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long timetableCourseId = responseBody(createResult)
                .path("timetableCourseId")
                .asLong();

        performAuthenticated(accessToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(timetableCourseId))
                .andExpect(jsonPath("$.courses[0].courseId").value(nullValue()))
                .andExpect(jsonPath("$.courses[0].courseName")
                        .value("개인 프로젝트"))
                .andExpect(jsonPath("$.courses[0].credit").value(2.5))
                .andExpect(jsonPath("$.courses[0].curriculumType")
                        .value(nullValue()))
                .andExpect(jsonPath("$.courses[0].expectedGrade")
                        .value(nullValue()))
                .andExpect(jsonPath("$.termSummary.totalCredits").value(2.5))
                .andExpect(jsonPath("$.termSummary.ungradedCourseCount").value(1));

        updateGrade(accessToken, timetableCourseId, "\"A+\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].expectedGrade").value("A+"))
                .andExpect(jsonPath("$.termSummary.totalCredits").value(2.5))
                .andExpect(jsonPath("$.termSummary.gpaCredits").value(2.5))
                .andExpect(jsonPath("$.termSummary.averageGpa").value(4.50));

        performAuthenticated(accessToken, post(
                        TIMETABLE_COURSES_PATH + "/import"
                )
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(timetableCourseId))
                .andExpect(jsonPath("$.courses[0].courseId").value(nullValue()))
                .andExpect(jsonPath("$.courses[0].courseName")
                        .value("개인 프로젝트"))
                .andExpect(jsonPath("$.courses[0].credit").value(2.5))
                .andExpect(jsonPath("$.courses[0].expectedGrade").value("A+"))
                .andExpect(jsonPath("$.termSummary.averageGpa").value(4.50));
    }

    @Test
    void customCourseNameAndCreditRejectNullOrInvalidValues() throws Exception {
        importMajorFixture(2026, 1);
        String accessToken = registerAndLogin("grade-custom-validation-user");
        long timetableId = createTimetable(accessToken, 2026, 1);
        long timetableCourseId = addCourse(
                accessToken,
                timetableId,
                offeringIdByTermAndCode(2026, 1, ALPHA_CODE)
        );

        updateTimetableCourse(accessToken, timetableCourseId, "{\"courseName\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("과목명은 필수입니다."));

        updateTimetableCourse(accessToken, timetableCourseId, "{\"courseName\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("과목명은 필수입니다."));

        updateTimetableCourse(accessToken, timetableCourseId, "{\"credit\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("학점은 필수입니다."));

        updateTimetableCourse(accessToken, timetableCourseId, "{\"credit\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("학점은 0보다 커야 합니다."));

        updateTimetableCourse(accessToken, timetableCourseId, "{\"credit\":20.001}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("한 과목의 학점은 20 이하여야 합니다."));

        updateTimetableCourse(accessToken, timetableCourseId, "{\"credit\":1.0001}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("학점은 소수 셋째 자리까지 입력할 수 있습니다."));

        updateTimetableCourse(
                accessToken,
                timetableCourseId,
                objectMapper.writeValueAsString(Map.of(
                        "courseName",
                        "가".repeat(256)
                ))
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("과목명은 255자 이하여야 합니다."));
    }

    @Test
    void selectedTermUsesOfferingCreditAndExpectedGradeCanBeSavedAndCleared()
            throws Exception {
        importMajorFixture(2026, 1);
        String accessToken = registerAndLogin("grade-update-user");
        UUID alphaId = offeringIdByTermAndCode(2026, 1, ALPHA_CODE);
        long timetableId = createTimetable(accessToken, 2026, 1);
        long timetableCourseId = addCourse(accessToken, timetableId, alphaId);

        performAuthenticated(accessToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(timetableId))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(timetableCourseId))
                .andExpect(jsonPath("$.courses[0].courseName").value("알파개론"))
                .andExpect(jsonPath("$.courses[0].credit").value(1.0))
                .andExpect(jsonPath("$.courses[0].expectedGrade")
                        .value(nullValue()))
                .andExpect(jsonPath("$.termSummary.totalCredits").value(1.0))
                .andExpect(jsonPath("$.termSummary.gpaCredits").value(0))
                .andExpect(jsonPath("$.termSummary.averageGpa")
                        .value(nullValue()))
                .andExpect(jsonPath("$.termSummary.ungradedCourseCount").value(1))
                .andExpect(jsonPath("$.termSummary.status").value("INCOMPLETE"));

        updateGrade(accessToken, timetableCourseId, "\"A+\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].credit").value(1.0))
                .andExpect(jsonPath("$.courses[0].expectedGrade").value("A+"))
                .andExpect(jsonPath("$.termSummary.totalCredits").value(1.0))
                .andExpect(jsonPath("$.termSummary.gpaCredits").value(1.0))
                .andExpect(jsonPath("$.termSummary.earnedCredits").value(1.0))
                .andExpect(jsonPath("$.termSummary.averageGpa").value(4.50))
                .andExpect(jsonPath("$.termSummary.ungradedCourseCount").value(0))
                .andExpect(jsonPath("$.termSummary.status").value("COMPLETE"))
                .andExpect(jsonPath("$.cumulativeSummary.totalCredits").value(1.0))
                .andExpect(jsonPath("$.cumulativeSummary.averageGpa").value(4.50));

        performAuthenticated(accessToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].expectedGrade").value("A+"));

        updateGrade(accessToken, timetableCourseId, "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].expectedGrade")
                        .value(nullValue()))
                .andExpect(jsonPath("$.termSummary.gpaCredits").value(0))
                .andExpect(jsonPath("$.termSummary.earnedCredits").value(0))
                .andExpect(jsonPath("$.termSummary.averageGpa")
                        .value(nullValue()))
                .andExpect(jsonPath("$.termSummary.ungradedCourseCount").value(1))
                .andExpect(jsonPath("$.termSummary.status").value("INCOMPLETE"));

        performAuthenticated(accessToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].expectedGrade")
                        .value(nullValue()));
    }

    @Test
    void timetableGradeDataIsIsolatedByAuthenticatedOwner() throws Exception {
        importMajorFixture(2026, 1);
        String ownerToken = registerAndLogin("grade-owner");
        String otherToken = registerAndLogin("other-grade-owner");
        UUID alphaId = offeringIdByTermAndCode(2026, 1, ALPHA_CODE);
        UUID charlieId = offeringIdByTermAndCode(2026, 1, CHARLIE_CODE);

        long ownerTimetableId = createTimetable(ownerToken, 2026, 1);
        long ownerCourseId = addCourse(ownerToken, ownerTimetableId, alphaId);
        long otherTimetableId = createTimetable(otherToken, 2026, 1);
        long otherCourseId = addCourse(otherToken, otherTimetableId, charlieId);
        updateGrade(ownerToken, ownerCourseId, "\"A\"")
                .andExpect(status().isOk());

        updateGrade(otherToken, ownerCourseId, "\"F\"")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIMETABLE_COURSE_NOT_FOUND"));

        performAuthenticated(otherToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(otherTimetableId))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(otherCourseId))
                .andExpect(jsonPath("$.courses[0].courseName").value("찰리실습"))
                .andExpect(jsonPath("$.courses[0].expectedGrade")
                        .value(nullValue()))
                .andExpect(jsonPath("$.cumulativeSummary.totalCredits").value(2.0))
                .andExpect(jsonPath("$.cumulativeSummary.averageGpa")
                        .value(nullValue()));

        performAuthenticated(ownerToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(ownerTimetableId))
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].expectedGrade").value("A"))
                .andExpect(jsonPath("$.cumulativeSummary.totalCredits").value(1.0))
                .andExpect(jsonPath("$.cumulativeSummary.averageGpa").value(4.00));
    }

    @Test
    void overviewIsNewestFirstAndTimetableDetailIncludesWeightedSummaries()
            throws Exception {
        importMajorFixture(2026, 1);
        importMajorFixture(2025, 2);
        String accessToken = registerAndLogin("grade-overview-user");

        long latestTimetableId = createTimetable(accessToken, 2026, 1);
        long latestCourseId = addCourse(
                accessToken,
                latestTimetableId,
                offeringIdByTermAndCode(2026, 1, ALPHA_CODE)
        );
        long olderTimetableId = createTimetable(accessToken, 2025, 2);
        long olderCourseId = addCourse(
                accessToken,
                olderTimetableId,
                offeringIdByTermAndCode(2025, 2, BETA_CODE)
        );
        updateGrade(accessToken, latestCourseId, "\"A+\"")
                .andExpect(status().isOk());
        updateGrade(accessToken, olderCourseId, "\"B\"")
                .andExpect(status().isOk());

        performAuthenticated(accessToken, get(OVERVIEW_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maximumGpa").value(4.5))
                .andExpect(jsonPath("$.cumulativeSummary.totalCredits").value(4.0))
                .andExpect(jsonPath("$.cumulativeSummary.gpaCredits").value(4.0))
                .andExpect(jsonPath("$.cumulativeSummary.earnedCredits").value(4.0))
                .andExpect(jsonPath("$.cumulativeSummary.averageGpa").value(3.38))
                .andExpect(jsonPath("$.cumulativeSummary.status").value("COMPLETE"))
                .andExpect(jsonPath("$.terms.length()").value(2))
                .andExpect(jsonPath("$.terms[0].timetableId")
                        .value(latestTimetableId))
                .andExpect(jsonPath("$.terms[0].year").value(2026))
                .andExpect(jsonPath("$.terms[0].semester").value(1))
                .andExpect(jsonPath("$.terms[0].courseCount").value(1))
                .andExpect(jsonPath("$.terms[0].summary.totalCredits").value(1.0))
                .andExpect(jsonPath("$.terms[0].summary.averageGpa").value(4.50))
                .andExpect(jsonPath("$.terms[1].timetableId")
                        .value(olderTimetableId))
                .andExpect(jsonPath("$.terms[1].year").value(2025))
                .andExpect(jsonPath("$.terms[1].semester").value(2))
                .andExpect(jsonPath("$.terms[1].courseCount").value(1))
                .andExpect(jsonPath("$.terms[1].summary.totalCredits").value(3.0))
                .andExpect(jsonPath("$.terms[1].summary.averageGpa").value(3.00));

        performAuthenticated(accessToken, get(TIMETABLE_COURSES_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].credit").value(1.0))
                .andExpect(jsonPath("$.termSummary.totalCredits").value(1.0))
                .andExpect(jsonPath("$.termSummary.averageGpa").value(4.50))
                .andExpect(jsonPath("$.cumulativeSummary.totalCredits").value(4.0))
                .andExpect(jsonPath("$.cumulativeSummary.averageGpa").value(3.38));

        performAuthenticated(accessToken, get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(latestTimetableId))
                .andExpect(jsonPath("$.courses[0].credit").value(1.0))
                .andExpect(jsonPath("$.gradeSummary.termSummary.totalCredits")
                        .value(1.0))
                .andExpect(jsonPath("$.gradeSummary.termSummary.averageGpa")
                        .value(4.50))
                .andExpect(jsonPath("$.gradeSummary.cumulativeSummary.totalCredits")
                        .value(4.0))
                .andExpect(jsonPath("$.gradeSummary.cumulativeSummary.averageGpa")
                        .value(3.38));
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

    private void assertAuthenticationRequired(MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
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

    private ResultActions updateGrade(
            String token,
            long timetableCourseId,
            String expectedGradeJson
    ) throws Exception {
        return updateTimetableCourse(
                token,
                timetableCourseId,
                "{\"expectedGrade\":" + expectedGradeJson + "}"
        );
    }

    private ResultActions updateTimetableCourse(
            String token,
            long timetableCourseId,
            String requestJson
    ) throws Exception {
        return performAuthenticated(token, patch(
                        TIMETABLE_COURSES_PATH + "/{timetableCourseId}",
                        timetableCourseId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson));
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

    private void importMajorFixture(int year, int semester) throws Exception {
        String term = year + "-" + semester;
        String payload = new ClassPathResource(FIXTURE)
                .getContentAsString(StandardCharsets.UTF_8)
                .replace(FIXTURE_IMPORT_ID, "course-search-major-" + term)
                .replace(FIXTURE_HASH, String.format("%064x", year * 10 + semester))
                .replace("\"academicYear\": 2026", "\"academicYear\": " + year)
                .replace("\"semester\": 1", "\"semester\": " + semester);
        CourseImportResponse response = courseImportService.importCourses(
                objectMapper.readValue(payload, TimetableParseResultRequest.class)
        );
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.STORED);
        assertThat(response.databaseChanged()).isTrue();
        assertThat(response.offeringCount()).isEqualTo(5);
    }

    private UUID offeringIdByTermAndCode(int year, int semester, String courseCode) {
        return jdbcTemplate.queryForObject(
                """
                select offering.id
                from course_offerings offering
                join courses course on course.id = offering.course_id
                join semesters semester on semester.id = offering.semester_id
                where offering.active = true
                  and semester.academic_year = ?
                  and semester.semester = ?
                  and course.course_code = ?
                limit 1
                """,
                UUID.class,
                year,
                semester,
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
}
