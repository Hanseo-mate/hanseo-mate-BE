package hsu.hanseomate.domain.timetable.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.courseimport.dto.CourseImportResponse;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import hsu.hanseomate.domain.courseimport.service.CourseImportService;
import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimetableApiIntegrationTest {

    private static final String FIXTURE =
            "fixtures/course-import/course-search-major-2026-1.json";
    private static final String GENERAL_FIXTURE =
            "fixtures/course-import/course-search-general-2026-1.json";
    private static final String SECTION_FIXTURE =
            "fixtures/course-import/major-ready-2026-1-a.json";
    private static final String TIMETABLE_PATH = "/api/timetables";
    private static final String ALPHA_CODE = "003000";
    private static final String CHARLIE_CODE = "001000";
    private static final String OTHER_CODE = "005000";
    private static final String GENERAL_REQUIRED_CODE = "101000";
    private static final String GENERAL_AREA_1_CODE = "102000";
    private static final String GENERAL_OCU_CODE = "105000";

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
    private JdbcTemplate jdbcTemplate;

    private Long authenticatedUserId;
    private String accessToken;

    @BeforeEach
    void cleanDatabaseBeforeTest() throws Exception {
        cleanDatabase();
        AuthSession authSession = registerAndLogin("timetable-user");
        authenticatedUserId = authSession.userId();
        accessToken = authSession.accessToken();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void createsTimetable() throws Exception {
        MvcResult result = createTimetableRequest(2026, 1)
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/timetables?year=2026&semester=1"
                ))
                .andExpect(jsonPath("$.timetableId").isNumber())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.semester").value(1))
                .andReturn();

        long timetableId = responseBody(result).path("timetableId").asLong();
        Timetable saved = timetableRepository.findById(timetableId).orElseThrow();
        assertThat(saved.getOwnerId()).isEqualTo(authenticatedUserId);
        assertThat(saved.getAcademicYear()).isEqualTo(2026);
        assertThat(saved.getSemester()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateTimetableForSameOwnerAndTerm() throws Exception {
        createTimetable(2026, 1);

        expectTimetableError(
                createTimetableRequest(2026, 1),
                HttpStatus.CONFLICT,
                "TIMETABLE_ALREADY_EXISTS",
                "해당 연도와 학기의 시간표가 이미 존재합니다.",
                TIMETABLE_PATH
        );

        assertThat(timetableRepository.count()).isEqualTo(1);
    }

    @Test
    void createsTimetableForDifferentSemester() throws Exception {
        long firstSemesterId = createTimetable(2026, 1);
        long secondSemesterId = createTimetable(2026, 2);

        assertThat(secondSemesterId).isNotEqualTo(firstSemesterId);
        assertThat(timetableRepository.count()).isEqualTo(2);
        assertThat(timetableRepository.existsByOwnerIdAndAcademicYearAndSemester(
                authenticatedUserId, 2026, 1
        )).isTrue();
        assertThat(timetableRepository.existsByOwnerIdAndAcademicYearAndSemester(
                authenticatedUserId, 2026, 2
        )).isTrue();
    }

    @Test
    void getsTimetableByYearAndSemester() throws Exception {
        long timetableId = createTimetable(2026, 1);

        performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(timetableId))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.semester").value(1))
                .andExpect(jsonPath("$.courses").isEmpty())
                .andExpect(jsonPath("$.cyberCourses").isEmpty());
    }

    @Test
    void listsOnlyCurrentUsersCreatedTimetableTermsNewestFirst() throws Exception {
        long oldestId = createTimetable(2025, 2);
        long newestId = createTimetable(2026, 2);
        long middleId = createTimetable(2026, 1);
        AuthSession otherUser = registerAndLogin("other-term-owner");
        timetableRepository.saveAndFlush(
                Timetable.create(otherUser.userId(), 2099, 1)
        );

        performAuthenticated(get(TIMETABLE_PATH + "/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].timetableId").value(newestId))
                .andExpect(jsonPath("$[0].year").value(2026))
                .andExpect(jsonPath("$[0].semester").value(2))
                .andExpect(jsonPath("$[1].timetableId").value(middleId))
                .andExpect(jsonPath("$[1].year").value(2026))
                .andExpect(jsonPath("$[1].semester").value(1))
                .andExpect(jsonPath("$[2].timetableId").value(oldestId))
                .andExpect(jsonPath("$[2].year").value(2025))
                .andExpect(jsonPath("$[2].semester").value(2));
    }

    @Test
    void returnsEmptyListWhenCurrentUserHasNoTimetables() throws Exception {
        performAuthenticated(get(TIMETABLE_PATH + "/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void timetableTermListRequiresAuthentication() throws Exception {
        mockMvc.perform(get(TIMETABLE_PATH + "/terms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customCourseCreationRequiresAuthentication() throws Exception {
        mockMvc.perform(post(TIMETABLE_PATH + "/1/custom-courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseName", "개인 프로젝트",
                                "credit", 2,
                                "dayOfWeek", "MONDAY",
                                "startTime", "10:00",
                                "endTime", "11:30"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addsCustomCourseToOwnedTimetableAndReturnsItInTimetable() throws Exception {
        long timetableId = createTimetable(2026, 1);

        MvcResult result = addCustomCourseRequest(
                timetableId,
                "  개인 프로젝트  ",
                2.5,
                "WEDNESDAY",
                "13:00",
                "14:30"
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timetableCourseId").isNumber())
                .andExpect(jsonPath("$.customCourse").value(true))
                .andExpect(jsonPath("$.courseId").value(nullValue()))
                .andExpect(jsonPath("$.courseCode").value(nullValue()))
                .andExpect(jsonPath("$.courseName").value("개인 프로젝트"))
                .andExpect(jsonPath("$.credit").value(2.5))
                .andExpect(jsonPath("$.cyber").value(false))
                .andExpect(jsonPath("$.meetings.length()").value(1))
                .andExpect(jsonPath("$.meetings[0].dayOfWeek").value("WEDNESDAY"))
                .andExpect(jsonPath("$.meetings[0].periods").isEmpty())
                .andExpect(jsonPath("$.meetings[0].startTime").value("13:00"))
                .andExpect(jsonPath("$.meetings[0].endTime").value("14:30"))
                .andExpect(jsonPath("$.meetings[0].classroom").value(nullValue()))
                .andReturn();

        long timetableCourseId = responseBody(result).path("timetableCourseId").asLong();
        TimetableCourse saved = timetableCourseRepository.findById(timetableCourseId)
                .orElseThrow();
        assertThat(saved.getTimetable().getId()).isEqualTo(timetableId);
        assertThat(saved.getCourseOffering()).isNull();
        assertThat(saved.getCustomCourseName()).isEqualTo("개인 프로젝트");
        assertThat(saved.getCustomCredit()).isEqualByComparingTo(new BigDecimal("2.5"));
        assertThat(saved.getCustomDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(saved.getCustomStartTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(saved.getCustomEndTime()).isEqualTo(LocalTime.of(14, 30));

        performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(timetableCourseId))
                .andExpect(jsonPath("$.courses[0].customCourse").value(true))
                .andExpect(jsonPath("$.courses[0].courseName").value("개인 프로젝트"))
                .andExpect(jsonPath("$.cyberCourses").isEmpty())
                .andExpect(jsonPath("$.gradeSummary.termSummary.totalCredits")
                        .value(2.5))
                .andExpect(jsonPath("$.gradeSummary.termSummary.ungradedCourseCount")
                        .value(1));

        performAuthenticated(delete(
                        TIMETABLE_PATH + "/courses/{timetableCourseId}",
                        timetableCourseId
                ))
                .andExpect(status().isNoContent());
        assertThat(timetableCourseRepository.existsById(timetableCourseId)).isFalse();
    }

    @Test
    void customCourseRejectsInvalidTimeRange() throws Exception {
        long timetableId = createTimetable(2026, 1);

        expectTimetableError(
                addCustomCourseRequest(
                        timetableId,
                        "잘못된 시간",
                        2,
                        "MONDAY",
                        "14:00",
                        "14:00"
                ),
                HttpStatus.BAD_REQUEST,
                "INVALID_CUSTOM_COURSE_TIME_RANGE",
                "종료 시간은 시작 시간보다 늦어야 합니다.",
                TIMETABLE_PATH + "/" + timetableId + "/custom-courses"
        );
        assertThat(timetableCourseRepository.count()).isZero();
    }

    @Test
    void customCourseConflictDoesNotDeleteOrReplaceExistingCourse() throws Exception {
        long timetableId = createTimetable(2026, 1);
        long firstCourseId = addCustomCourse(
                timetableId,
                "첫 번째 일정",
                2,
                "TUESDAY",
                "10:00",
                "11:00"
        );

        expectTimetableError(
                addCustomCourseRequest(
                        timetableId,
                        "겹치는 일정",
                        3,
                        "TUESDAY",
                        "10:30",
                        "11:30"
                ),
                HttpStatus.CONFLICT,
                "TIMETABLE_TIME_CONFLICT",
                "기존 과목과 수업 시간이 겹칩니다.",
                TIMETABLE_PATH + "/" + timetableId + "/custom-courses"
        )
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].timetableCourseId")
                        .value(firstCourseId))
                .andExpect(jsonPath("$.conflicts[0].customCourse").value(true));

        assertThat(timetableCourseRepository.count()).isEqualTo(1);
        assertThat(timetableCourseRepository.findById(firstCourseId)).isPresent();

        addCustomCourseRequest(
                timetableId,
                "이어지는 일정",
                1,
                "TUESDAY",
                "11:00",
                "12:00"
        ).andExpect(status().isCreated());
        assertThat(timetableCourseRepository.count()).isEqualTo(2);
    }

    @Test
    void customCourseCanOnlyBeAddedToCurrentUsersTimetable() throws Exception {
        AuthSession otherUser = registerAndLogin("custom-course-owner");
        Timetable otherOwnersTimetable = timetableRepository.saveAndFlush(
                Timetable.create(otherUser.userId(), 2026, 1)
        );

        expectTimetableError(
                addCustomCourseRequest(
                        otherOwnersTimetable.getId(),
                        "남의 시간표 과목",
                        2,
                        "FRIDAY",
                        "09:00",
                        "10:00"
                ),
                HttpStatus.FORBIDDEN,
                "TIMETABLE_ACCESS_DENIED",
                "해당 시간표에 접근할 권한이 없습니다.",
                TIMETABLE_PATH + "/" + otherOwnersTimetable.getId()
                        + "/custom-courses"
        );
        assertThat(timetableCourseRepository.count()).isZero();
    }

    @Test
    void groupsCyberCoursesSeparatelyWithoutDuplicatingCourses() throws Exception {
        importMajorFixture(payload -> payload.replaceFirst(
                "\"note\": null",
                "\"note\": \"온라인수업\""
        ));
        CourseOffering cyberCourse = offeringByCode(ALPHA_CODE);
        CourseOffering regularCourse = offeringByCode(CHARLIE_CODE);
        long timetableId = createTimetable(2026, 1);

        addCourseRequest(timetableId, cyberCourse.getId(), "REJECT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cyber").value(true));
        addCourseRequest(timetableId, regularCourse.getId(), "REJECT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cyber").value(false));

        performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].courseId")
                        .value(regularCourse.getId().toString()))
                .andExpect(jsonPath("$.courses[0].cyber").value(false))
                .andExpect(jsonPath("$.cyberCourses.length()").value(1))
                .andExpect(jsonPath("$.cyberCourses[0].courseId")
                        .value(cyberCourse.getId().toString()))
                .andExpect(jsonPath("$.cyberCourses[0].cyber").value(true));
    }

    @Test
    void returnsGeneralCategoryDepartmentsAndSectionForGeneralEducationCourses()
            throws Exception {
        importGeneralFixture(payload -> payload.replaceFirst(
                "\\\"eligibleDepartmentNames\\\": \\[\\]",
                "\"eligibleDepartmentNames\": "
                        + "[\"항공소프트웨어공학과\", \"항공운항학과\"]"
        ));
        CourseOffering requiredCourse = offeringByCode(GENERAL_REQUIRED_CODE);
        CourseOffering ocuCourse = offeringByCode(GENERAL_OCU_CODE);
        long timetableId = createTimetable(2026, 1);

        addCourseRequest(timetableId, requiredCourse.getId(), "REJECT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sectionNo").value("01"))
                .andExpect(jsonPath("$.generalCategory").value("REQUIRED"))
                .andExpect(jsonPath("$.eligibleDepartmentNames", containsInAnyOrder(
                        "항공소프트웨어공학과",
                        "항공운항학과"
                )))
                .andExpect(jsonPath("$.cyber").value(false));
        addCourseRequest(timetableId, ocuCourse.getId(), "REJECT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sectionNo").value("01"))
                .andExpect(jsonPath("$.generalCategory").value("OCU"))
                .andExpect(jsonPath("$.eligibleDepartmentNames").isEmpty())
                .andExpect(jsonPath("$.cyber").value(true));

        performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].courseId")
                        .value(requiredCourse.getId().toString()))
                .andExpect(jsonPath("$.courses[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.courses[0].generalCategory").value("REQUIRED"))
                .andExpect(jsonPath("$.courses[0].eligibleDepartmentNames", containsInAnyOrder(
                        "항공소프트웨어공학과",
                        "항공운항학과"
                )))
                .andExpect(jsonPath("$.cyberCourses.length()").value(1))
                .andExpect(jsonPath("$.cyberCourses[0].courseId")
                        .value(ocuCourse.getId().toString()))
                .andExpect(jsonPath("$.cyberCourses[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.cyberCourses[0].generalCategory").value("OCU"))
                .andExpect(jsonPath("$.cyberCourses[0].eligibleDepartmentNames").isEmpty());
    }

    @Test
    void getTimetableIncludesStoredCoursesAndAllMeetingDetails() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(OTHER_CODE);
        long timetableId = createTimetable(2026, 1);
        long timetableCourseId = addCourse(timetableId, course.getId(), null);

        performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(timetableCourseId))
                .andExpect(jsonPath("$.courses[0].courseId")
                        .value(course.getId().toString()))
                .andExpect(jsonPath("$.courses[0].courseCode").value(OTHER_CODE))
                .andExpect(jsonPath("$.courses[0].courseName").value("기타세미나"))
                .andExpect(jsonPath("$.courses[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.courses[0].credit").value(5.0))
                .andExpect(jsonPath("$.courses[0].eligibleDepartmentNames[0]")
                        .value("자유전공학부"))
                .andExpect(jsonPath("$.courses[0].instructorName").value("윤교수"))
                .andExpect(jsonPath("$.courses[0].scheduleText").value("월1,2 / 금8,9"))
                .andExpect(jsonPath("$.courses[0].classroomText").value("미래관 501호"))
                .andExpect(jsonPath("$.courses[0].meetings.length()").value(2))
                .andExpect(jsonPath("$.courses[0].meetings[0].dayOfWeek")
                        .value("MONDAY"))
                .andExpect(jsonPath("$.courses[0].meetings[0].periods[0]").value(1))
                .andExpect(jsonPath("$.courses[0].meetings[0].periods[1]").value(2))
                .andExpect(jsonPath("$.courses[0].meetings[0].classroom.originalValue")
                        .value("미래관 501호"))
                .andExpect(jsonPath("$.courses[0].meetings[1].dayOfWeek")
                        .value("FRIDAY"))
                .andExpect(jsonPath("$.courses[0].meetings[1].periods[0]").value(8))
                .andExpect(jsonPath("$.courses[0].meetings[1].periods[1]").value(9));
    }

    @Test
    void getMissingTimetableReturnsNotFoundError() throws Exception {
        expectTimetableError(
                performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1")),
                HttpStatus.NOT_FOUND,
                "TIMETABLE_NOT_FOUND",
                "시간표를 찾을 수 없습니다.",
                TIMETABLE_PATH
        );
    }

    @Test
    void addsCourseWithDefaultRejectPolicy() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);
        long timetableId = createTimetable(2026, 1);

        MvcResult result = addCourseRequest(timetableId, course.getId(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timetableCourseId").isNumber())
                .andExpect(jsonPath("$.courseId").value(course.getId().toString()))
                .andExpect(jsonPath("$.courseName").value("알파개론"))
                .andExpect(jsonPath("$.meetings.length()").value(1))
                .andExpect(jsonPath("$.meetings[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.meetings[0].periods[0]").value(0))
                .andExpect(jsonPath("$.meetings[0].periods[1]").value(1))
                .andReturn();

        long timetableCourseId = responseBody(result).path("timetableCourseId").asLong();
        TimetableCourse saved = timetableCourseRepository.findById(timetableCourseId)
                .orElseThrow();
        assertThat(saved.getTimetable().getId()).isEqualTo(timetableId);
        assertThat(saved.getCourseOffering().getId()).isEqualTo(course.getId());
    }

    @Test
    void rejectsDuplicateCourseId() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);
        long timetableId = createTimetable(2026, 1);
        addCourse(timetableId, course.getId(), "REJECT");

        expectTimetableError(
                addCourseRequest(timetableId, course.getId(), "REJECT"),
                HttpStatus.CONFLICT,
                "COURSE_ALREADY_ADDED",
                "이미 시간표에 추가된 과목입니다.",
                TIMETABLE_PATH + "/courses/" + timetableId
        );

        assertThat(timetableCourseRepository.count()).isEqualTo(1);
    }

    @Test
    void replacePolicyStillRejectsDuplicateCourseId() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);
        long timetableId = createTimetable(2026, 1);
        addCourse(timetableId, course.getId(), "REJECT");

        expectTimetableError(
                addCourseRequest(timetableId, course.getId(), "REPLACE"),
                HttpStatus.CONFLICT,
                "COURSE_ALREADY_ADDED",
                "이미 시간표에 추가된 과목입니다.",
                TIMETABLE_PATH + "/courses/" + timetableId
        );

        assertThat(timetableCourseRepository.count()).isEqualTo(1);
    }

    @Test
    void sameCourseNameWithDifferentCourseIdsIsNotDuplicate() throws Exception {
        importMajorFixture(payload -> payload.replace(
                "\"courseName\": \"찰리실습\"",
                "\"courseName\": \"알파개론\""
        ));
        CourseOffering first = offeringByCode(ALPHA_CODE);
        CourseOffering second = offeringByCode(CHARLIE_CODE);
        long timetableId = createTimetable(2026, 1);

        addCourse(timetableId, first.getId(), "REJECT");
        addCourse(timetableId, second.getId(), "REJECT");

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getCourseName()).isEqualTo(second.getCourseName());
        assertThat(timetableCourseRepository.count()).isEqualTo(2);
        assertThat(timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId, first.getId()
        )).isTrue();
        assertThat(timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId, second.getId()
        )).isTrue();
    }

    @Test
    void sameCourseCodeWithDifferentSectionsKeepsIndependentTimetableSchedules()
            throws Exception {
        importTwoSectionFixture();
        CourseOffering firstSection = offeringByCodeAndSection("001234", "01");
        CourseOffering secondSection = offeringByCodeAndSection("001234", "02");
        long timetableId = createTimetable(2026, 1);

        assertThat(firstSection.getId()).isNotEqualTo(secondSection.getId());
        assertThat(firstSection.getCourseName()).isEqualTo(secondSection.getCourseName());

        addCourseRequest(timetableId, firstSection.getId(), "REJECT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseName").value("웹프로그래밍"))
                .andExpect(jsonPath("$.sectionNo").value("01"))
                .andExpect(jsonPath("$.instructorName").value("홍길동"))
                .andExpect(jsonPath("$.meetings[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.meetings[0].periods[0]").value(1))
                .andExpect(jsonPath("$.meetings[0].classroom.originalValue")
                        .value("본관 101호"));
        addCourseRequest(timetableId, secondSection.getId(), "REJECT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseName").value("웹프로그래밍"))
                .andExpect(jsonPath("$.sectionNo").value("02"))
                .andExpect(jsonPath("$.instructorName").value("김분반"))
                .andExpect(jsonPath("$.meetings[0].dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$.meetings[0].periods[0]").value(4))
                .andExpect(jsonPath("$.meetings[0].classroom.originalValue")
                        .value("후속관 909호"));

        assertThat(timetableCourseRepository.count()).isEqualTo(2);
        performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(2))
                .andExpect(jsonPath("$.courses[0].courseId")
                        .value(firstSection.getId().toString()))
                .andExpect(jsonPath("$.courses[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.courses[0].meetings[0].dayOfWeek")
                        .value("MONDAY"))
                .andExpect(jsonPath("$.courses[1].courseId")
                        .value(secondSection.getId().toString()))
                .andExpect(jsonPath("$.courses[1].sectionNo").value("02"))
                .andExpect(jsonPath("$.courses[1].meetings[0].dayOfWeek")
                        .value("TUESDAY"));
    }

    @Test
    void rejectsCourseFromDifferentAcademicYear() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);
        long timetableId = createTimetable(2025, 1);

        expectTimetableError(
                addCourseRequest(timetableId, course.getId(), "REJECT"),
                HttpStatus.BAD_REQUEST,
                "COURSE_TERM_MISMATCH",
                "시간표와 과목의 연도 또는 학기가 일치하지 않습니다.",
                TIMETABLE_PATH + "/courses/" + timetableId
        );

        assertThat(timetableCourseRepository.count()).isZero();
    }

    @Test
    void rejectsCourseFromDifferentSemester() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);
        long timetableId = createTimetable(2026, 2);

        expectTimetableError(
                addCourseRequest(timetableId, course.getId(), "REJECT"),
                HttpStatus.BAD_REQUEST,
                "COURSE_TERM_MISMATCH",
                "시간표와 과목의 연도 또는 학기가 일치하지 않습니다.",
                TIMETABLE_PATH + "/courses/" + timetableId
        );

        assertThat(timetableCourseRepository.count()).isZero();
    }

    @Test
    void overlappingPeriodsOnSameDayReturnConflictDetails() throws Exception {
        importMajorFixture();
        CourseOffering alpha = offeringByCode(ALPHA_CODE);
        CourseOffering other = offeringByCode(OTHER_CODE);
        long timetableId = createTimetable(2026, 1);
        long alphaTimetableCourseId = addCourse(timetableId, alpha.getId(), "REJECT");

        expectTimetableError(
                addCourseRequest(timetableId, other.getId(), "REJECT"),
                HttpStatus.CONFLICT,
                "TIMETABLE_TIME_CONFLICT",
                "기존 과목과 수업 시간이 겹칩니다.",
                TIMETABLE_PATH + "/courses/" + timetableId
        )
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].timetableCourseId")
                        .value(alphaTimetableCourseId))
                .andExpect(jsonPath("$.conflicts[0].courseId")
                        .value(alpha.getId().toString()))
                .andExpect(jsonPath("$.conflicts[0].courseName").value("알파개론"))
                .andExpect(jsonPath("$.conflicts[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.conflicts[0].eligibleDepartmentNames[0]")
                        .value("항공소프트웨어공학과"))
                .andExpect(jsonPath("$.conflicts[0].meetings.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].meetings[0].dayOfWeek")
                        .value("MONDAY"))
                .andExpect(jsonPath("$.conflicts[0].meetings[0].periods[0]").value(0))
                .andExpect(jsonPath("$.conflicts[0].meetings[0].periods[1]").value(1));
    }

    @Test
    void generalCategoryIsIncludedInGeneralEducationConflictDetails() throws Exception {
        importGeneralFixture(payload -> payload
                .replaceFirst(
                        "\\\"dayOfWeek\\\": \\\"TUESDAY\\\"",
                        "\"dayOfWeek\": \"MONDAY\""
                )
                .replaceFirst(
                        "\\\"periods\\\": \\[2, 3\\]",
                        "\"periods\": [0, 1]"
                ));
        CourseOffering required = offeringByCode(GENERAL_REQUIRED_CODE);
        CourseOffering areaOne = offeringByCode(GENERAL_AREA_1_CODE);
        long timetableId = createTimetable(2026, 1);
        addCourse(timetableId, required.getId(), "REJECT");

        expectTimetableError(
                addCourseRequest(timetableId, areaOne.getId(), "REJECT"),
                HttpStatus.CONFLICT,
                "TIMETABLE_TIME_CONFLICT",
                "기존 과목과 수업 시간이 겹칩니다.",
                TIMETABLE_PATH + "/courses/" + timetableId
        )
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].courseId")
                        .value(required.getId().toString()))
                .andExpect(jsonPath("$.conflicts[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.conflicts[0].generalCategory").value("REQUIRED"))
                .andExpect(jsonPath("$.conflicts[0].eligibleDepartmentNames").isEmpty());
    }

    @Test
    void samePeriodsOnDifferentDaysDoNotConflict() throws Exception {
        importMajorFixture(payload -> payload
                .replace("\"scheduleText\": \"화2,3\"", "\"scheduleText\": \"화0,1\"")
                .replace("\"periods\": [2, 3]", "\"periods\": [0, 1]"));
        CourseOffering monday = offeringByCode(ALPHA_CODE);
        CourseOffering tuesday = offeringByCode(CHARLIE_CODE);
        long timetableId = createTimetable(2026, 1);

        addCourse(timetableId, monday.getId(), "REJECT");
        addCourse(timetableId, tuesday.getId(), "REJECT");

        assertThat(timetableCourseRepository.count()).isEqualTo(2);
    }

    @Test
    void adjacentPeriodsOnSameDayDoNotConflict() throws Exception {
        importMajorFixture(payload -> payload
                .replace("\"scheduleText\": \"화2,3\"", "\"scheduleText\": \"월2,3\"")
                .replace("\"dayOfWeek\": \"TUESDAY\"", "\"dayOfWeek\": \"MONDAY\""));
        CourseOffering periodsZeroAndOne = offeringByCode(ALPHA_CODE);
        CourseOffering periodsTwoAndThree = offeringByCode(CHARLIE_CODE);
        long timetableId = createTimetable(2026, 1);

        addCourse(timetableId, periodsZeroAndOne.getId(), "REJECT");
        addCourse(timetableId, periodsTwoAndThree.getId(), "REJECT");

        assertThat(timetableCourseRepository.count()).isEqualTo(2);
    }

    @Test
    void oneOverlapAmongMultipleMeetingDaysCausesOneConflictEntry() throws Exception {
        importMajorFixture();
        CourseOffering multipleMeetingCourse = offeringByCode(OTHER_CODE);
        CourseOffering mondayCourse = offeringByCode(ALPHA_CODE);
        long timetableId = createTimetable(2026, 1);
        long multipleMeetingId = addCourse(
                timetableId,
                multipleMeetingCourse.getId(),
                "REJECT"
        );

        expectTimetableError(
                addCourseRequest(timetableId, mondayCourse.getId(), "REJECT"),
                HttpStatus.CONFLICT,
                "TIMETABLE_TIME_CONFLICT",
                "기존 과목과 수업 시간이 겹칩니다.",
                TIMETABLE_PATH + "/courses/" + timetableId
        )
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].timetableCourseId")
                        .value(multipleMeetingId))
                .andExpect(jsonPath("$.conflicts[0].courseId")
                        .value(multipleMeetingCourse.getId().toString()))
                .andExpect(jsonPath("$.conflicts[0].meetings.length()").value(2))
                .andExpect(jsonPath("$.conflicts[0].meetings[0].dayOfWeek")
                        .value("MONDAY"))
                .andExpect(jsonPath("$.conflicts[0].meetings[1].dayOfWeek")
                        .value("FRIDAY"));
    }

    @Test
    void rejectPolicyLeavesExistingAndCandidateCoursesUnchanged() throws Exception {
        importMajorFixture();
        CourseOffering existing = offeringByCode(ALPHA_CODE);
        CourseOffering candidate = offeringByCode(OTHER_CODE);
        long timetableId = createTimetable(2026, 1);
        addCourse(timetableId, existing.getId(), "REJECT");

        expectTimetableError(
                addCourseRequest(timetableId, candidate.getId(), "REJECT"),
                HttpStatus.CONFLICT,
                "TIMETABLE_TIME_CONFLICT",
                "기존 과목과 수업 시간이 겹칩니다.",
                TIMETABLE_PATH + "/courses/" + timetableId
        );

        assertThat(timetableCourseRepository.count()).isEqualTo(1);
        assertThat(timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId, existing.getId()
        )).isTrue();
        assertThat(timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId, candidate.getId()
        )).isFalse();
    }

    @Test
    void replacePolicyDeletesConflictAndAddsCandidate() throws Exception {
        importMajorFixture();
        CourseOffering existing = offeringByCode(ALPHA_CODE);
        CourseOffering candidate = offeringByCode(OTHER_CODE);
        long timetableId = createTimetable(2026, 1);
        addCourse(timetableId, existing.getId(), "REJECT");

        addCourseRequest(timetableId, candidate.getId(), "REPLACE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(candidate.getId().toString()));

        assertThat(timetableCourseRepository.count()).isEqualTo(1);
        assertThat(timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId, existing.getId()
        )).isFalse();
        assertThat(timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId, candidate.getId()
        )).isTrue();
    }

    @Test
    void replacePolicyDeletesEveryCourseConflictingWithCandidate() throws Exception {
        importMajorFixture(payload -> payload
                .replace("\"scheduleText\": \"화2,3\"", "\"scheduleText\": \"월2,3\"")
                .replace("\"dayOfWeek\": \"TUESDAY\"", "\"dayOfWeek\": \"MONDAY\""));
        CourseOffering firstExisting = offeringByCode(ALPHA_CODE);
        CourseOffering secondExisting = offeringByCode(CHARLIE_CODE);
        CourseOffering candidate = offeringByCode(OTHER_CODE);
        long timetableId = createTimetable(2026, 1);
        addCourse(timetableId, firstExisting.getId(), "REJECT");
        addCourse(timetableId, secondExisting.getId(), "REJECT");

        addCourseRequest(timetableId, candidate.getId(), "REPLACE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(candidate.getId().toString()));

        assertThat(timetableCourseRepository.count()).isEqualTo(1);
        assertThat(timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId, firstExisting.getId()
        )).isFalse();
        assertThat(timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId, secondExisting.getId()
        )).isFalse();
        assertThat(timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId, candidate.getId()
        )).isTrue();
    }

    @Test
    void deletesTimetableCourse() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);
        long timetableId = createTimetable(2026, 1);
        long timetableCourseId = addCourse(timetableId, course.getId(), "REJECT");

        performAuthenticated(delete(
                        TIMETABLE_PATH + "/courses/{timetableCourseId}",
                        timetableCourseId
                ))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        assertThat(timetableCourseRepository.existsById(timetableCourseId)).isFalse();
        assertThat(timetableRepository.existsById(timetableId)).isTrue();
    }

    @Test
    void cannotDeleteAnotherUsersTimetableCourse() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);
        AuthSession otherUser = registerAndLogin("other-course-owner");
        Timetable otherTimetable = timetableRepository.saveAndFlush(
                Timetable.create(otherUser.userId(), 2026, 1)
        );
        TimetableCourse otherCourse = timetableCourseRepository.saveAndFlush(
                TimetableCourse.create(otherTimetable, course)
        );

        expectTimetableError(
                performAuthenticated(delete(
                        TIMETABLE_PATH + "/courses/{timetableCourseId}",
                        otherCourse.getId()
                )),
                HttpStatus.FORBIDDEN,
                "TIMETABLE_ACCESS_DENIED",
                "해당 시간표에 접근할 권한이 없습니다.",
                TIMETABLE_PATH + "/courses/" + otherCourse.getId()
        );

        assertThat(timetableCourseRepository.existsById(otherCourse.getId())).isTrue();
    }

    @Test
    void deletingTimetableDeletesConnectedTimetableCourses() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);
        long timetableId = createTimetable(2026, 1);
        long timetableCourseId = addCourse(timetableId, course.getId(), "REJECT");

        performAuthenticated(delete(TIMETABLE_PATH + "/{timetableId}", timetableId))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist());

        assertThat(timetableRepository.existsById(timetableId)).isFalse();
        assertThat(timetableCourseRepository.existsById(timetableCourseId)).isFalse();
        assertThat(timetableCourseRepository.count()).isZero();
    }

    @Test
    void reimportKeepsOfferingIdAndTimetableCourseForUnchangedCourse() throws Exception {
        importMajorFixture();
        CourseOffering oldOffering = offeringByCode(ALPHA_CODE);
        UUID oldOfferingId = oldOffering.getId();
        long timetableId = createTimetable(2026, 1);
        long timetableCourseId = addCourse(timetableId, oldOfferingId, "REJECT");

        String replacementPayload = fixturePayload()
                .replace(
                        "\"importId\": \"course-search-major-2026-1\"",
                        "\"importId\": \"course-search-major-2026-1-reimport\""
                )
                .replace(
                        "\"fileSha256\": \""
                                + "1".repeat(64)
                                + "\"",
                        "\"fileSha256\": \""
                                + "2".repeat(64)
                                + "\""
                );
        CourseImportResponse response = performImport(replacementPayload);

        assertThat(response.storageStatus()).isEqualTo(StorageStatus.STORED);
        assertThat(response.databaseChanged()).isTrue();
        CourseOffering preservedOffering = courseOfferingRepository.findById(oldOfferingId)
                .orElseThrow();
        assertThat(preservedOffering.isActive()).isTrue();
        assertThat(timetableCourseRepository.existsById(timetableCourseId)).isTrue();
        assertThat(timetableCourseRepository.count()).isEqualTo(1);
        assertThat(timetableRepository.existsById(timetableId)).isTrue();
        assertThat(courseOfferingRepository.count()).isEqualTo(5);

        performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(timetableCourseId))
                .andExpect(jsonPath("$.courses[0].courseId")
                        .value(oldOfferingId.toString()));
    }

    @Test
    void reimportUpdatesTimetableDetailsWhileKeepingOfferingAndTimetableCourse() throws Exception {
        importMajorFixture();
        CourseOffering originalOffering = offeringByCodeAndSection(ALPHA_CODE, "01");
        UUID originalOfferingId = originalOffering.getId();
        long timetableId = createTimetable(2026, 1);
        long timetableCourseId = addCourse(
                timetableId,
                originalOfferingId,
                "REJECT"
        );

        ObjectNode replacementPayload = (ObjectNode) objectMapper.readTree(fixturePayload());
        replacementPayload.put(
                "importId",
                "course-search-major-2026-1-updated-details"
        );
        replacementPayload.put(
                "fileName",
                "course-search-major-2026-1-updated-details.xlsx"
        );
        replacementPayload.put("fileSha256", "9".repeat(64));

        ObjectNode updatedLecture = null;
        for (JsonNode lectureNode : replacementPayload.path("lectures")) {
            if (ALPHA_CODE.equals(lectureNode.path("courseCode").stringValue())
                    && "01".equals(lectureNode.path("sectionNo").stringValue())) {
                updatedLecture = (ObjectNode) lectureNode;
                break;
            }
        }
        if (updatedLecture == null) {
            throw new AssertionError("수정할 테스트 강좌를 찾을 수 없습니다.");
        }
        updatedLecture.put("instructorName", "변경교수");
        updatedLecture.put("scheduleText", "화4,5");
        updatedLecture.put("classroomText", "수정관 707호");

        ObjectNode updatedSchedule = (ObjectNode) updatedLecture.path("schedules").get(0);
        updatedSchedule.put("dayOfWeek", "TUESDAY");
        ArrayNode updatedPeriods = (ArrayNode) updatedSchedule.path("periods");
        updatedPeriods.removeAll();
        updatedPeriods.add(4);
        updatedPeriods.add(5);
        ObjectNode updatedClassroom = (ObjectNode) updatedSchedule.path("classroom");
        updatedClassroom.put("buildingName", "수정관");
        updatedClassroom.put("roomNumber", "707");
        updatedClassroom.put("originalValue", "수정관 707호");

        CourseImportResponse response = performImport(
                objectMapper.writeValueAsString(replacementPayload)
        );

        assertThat(response.storageStatus()).isEqualTo(StorageStatus.STORED);
        assertThat(response.databaseChanged()).isTrue();
        CourseOffering preservedOffering = courseOfferingRepository
                .findDetailedById(originalOfferingId)
                .orElseThrow();
        assertThat(preservedOffering.isActive()).isTrue();
        assertThat(preservedOffering.getInstructorName()).isEqualTo("변경교수");
        assertThat(timetableCourseRepository.existsById(timetableCourseId)).isTrue();
        assertThat(timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId,
                originalOfferingId
        )).isTrue();
        assertThat(timetableCourseRepository.count()).isEqualTo(1);

        performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].timetableCourseId")
                        .value(timetableCourseId))
                .andExpect(jsonPath("$.courses[0].courseId")
                        .value(originalOfferingId.toString()))
                .andExpect(jsonPath("$.courses[0].instructorName").value("변경교수"))
                .andExpect(jsonPath("$.courses[0].scheduleText").value("화4,5"))
                .andExpect(jsonPath("$.courses[0].classroomText").value("수정관 707호"))
                .andExpect(jsonPath("$.courses[0].meetings.length()").value(1))
                .andExpect(jsonPath("$.courses[0].meetings[0].dayOfWeek")
                        .value("TUESDAY"))
                .andExpect(jsonPath("$.courses[0].meetings[0].periods[0]").value(4))
                .andExpect(jsonPath("$.courses[0].meetings[0].periods[1]").value(5))
                .andExpect(jsonPath("$.courses[0].meetings[0].classroom.buildingName")
                        .value("수정관"))
                .andExpect(jsonPath("$.courses[0].meetings[0].classroom.roomNumber")
                        .value("707"))
                .andExpect(jsonPath("$.courses[0].meetings[0].classroom.originalValue")
                        .value("수정관 707호"));
    }

    @Test
    void otherOwnersTimetableCannotBeReadChangedOrDeleted() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);
        AuthSession otherUser = registerAndLogin("other-timetable-user");
        Timetable otherOwnersTimetable = timetableRepository.saveAndFlush(
                Timetable.create(otherUser.userId(), 2026, 1)
        );

        expectTimetableError(
                performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")
                        .param("semester", "1")),
                HttpStatus.NOT_FOUND,
                "TIMETABLE_NOT_FOUND",
                "시간표를 찾을 수 없습니다.",
                TIMETABLE_PATH
        );
        expectTimetableError(
                addCourseRequest(otherOwnersTimetable.getId(), course.getId(), "REJECT"),
                HttpStatus.FORBIDDEN,
                "TIMETABLE_ACCESS_DENIED",
                "해당 시간표에 접근할 권한이 없습니다.",
                TIMETABLE_PATH + "/courses/" + otherOwnersTimetable.getId()
        );
        expectTimetableError(
                performAuthenticated(delete(
                        TIMETABLE_PATH + "/{timetableId}",
                        otherOwnersTimetable.getId()
                )),
                HttpStatus.FORBIDDEN,
                "TIMETABLE_ACCESS_DENIED",
                "해당 시간표에 접근할 권한이 없습니다.",
                TIMETABLE_PATH + "/" + otherOwnersTimetable.getId()
        );

        assertThat(timetableRepository.existsById(otherOwnersTimetable.getId())).isTrue();
        assertThat(timetableCourseRepository.count()).isZero();
    }

    @Test
    void timetableOwnerAndTermDatabaseUniqueConstraintRejectsDuplicateRows() {
        timetableRepository.saveAndFlush(
                Timetable.create(authenticatedUserId, 2026, 1)
        );

        assertThatThrownBy(() -> timetableRepository.saveAndFlush(
                Timetable.create(authenticatedUserId, 2026, 1)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void timetableAndCourseDatabaseUniqueConstraintRejectsDuplicateRows() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);
        long timetableId = createTimetable(2026, 1);
        Timetable timetable = timetableRepository.findById(timetableId).orElseThrow();
        timetableCourseRepository.saveAndFlush(TimetableCourse.create(timetable, course));

        assertThatThrownBy(() -> timetableCourseRepository.saveAndFlush(
                TimetableCourse.create(timetable, course)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void addingToUnknownTimetableReturnsNotFoundError() throws Exception {
        importMajorFixture();
        CourseOffering course = offeringByCode(ALPHA_CODE);

        expectTimetableError(
                addCourseRequest(999_999L, course.getId(), "REJECT"),
                HttpStatus.NOT_FOUND,
                "TIMETABLE_NOT_FOUND",
                "시간표를 찾을 수 없습니다.",
                TIMETABLE_PATH + "/courses/999999"
        );
    }

    @Test
    void addingUnknownCourseReturnsCourseNotFoundError() throws Exception {
        long timetableId = createTimetable(2026, 1);

        expectTimetableError(
                addCourseRequest(timetableId, UUID.randomUUID(), "REJECT"),
                HttpStatus.NOT_FOUND,
                "COURSE_NOT_FOUND",
                "과목을 찾을 수 없습니다.",
                TIMETABLE_PATH + "/courses/" + timetableId
        );

        assertThat(timetableCourseRepository.count()).isZero();
    }

    @Test
    void invalidYearOrSemesterReturnsInvalidTermError() throws Exception {
        expectTimetableError(
                createTimetableRequest(1999, 1),
                HttpStatus.BAD_REQUEST,
                "INVALID_TIMETABLE_TERM",
                "시간표의 연도 또는 학기 값이 유효하지 않습니다.",
                TIMETABLE_PATH
        );
        expectTimetableError(
                createTimetableRequest(2026, 3),
                HttpStatus.BAD_REQUEST,
                "INVALID_TIMETABLE_TERM",
                "시간표의 연도 또는 학기 값이 유효하지 않습니다.",
                TIMETABLE_PATH
        );
        expectTimetableError(
                performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2101")
                        .param("semester", "1")),
                HttpStatus.BAD_REQUEST,
                "INVALID_TIMETABLE_TERM",
                "시간표의 연도 또는 학기 값이 유효하지 않습니다.",
                TIMETABLE_PATH
        );

        assertThat(timetableRepository.count()).isZero();
    }

    @Test
    void missingYearOrSemesterReturnsInvalidTermError() throws Exception {
        expectTimetableError(
                performAuthenticated(get(TIMETABLE_PATH)
                        .param("semester", "1")),
                HttpStatus.BAD_REQUEST,
                "INVALID_TIMETABLE_TERM",
                "시간표의 연도 또는 학기 값이 유효하지 않습니다.",
                TIMETABLE_PATH
        );
        expectTimetableError(
                performAuthenticated(get(TIMETABLE_PATH)
                        .param("year", "2026")),
                HttpStatus.BAD_REQUEST,
                "INVALID_TIMETABLE_TERM",
                "시간표의 연도 또는 학기 값이 유효하지 않습니다.",
                TIMETABLE_PATH
        );
    }

    @Test
    void deletingUnknownTimetableCourseReturnsNotFoundError() throws Exception {
        expectTimetableError(
                performAuthenticated(delete(
                        TIMETABLE_PATH + "/courses/{timetableCourseId}",
                        999_999L
                )),
                HttpStatus.NOT_FOUND,
                "TIMETABLE_COURSE_NOT_FOUND",
                "시간표에서 해당 과목을 찾을 수 없습니다.",
                TIMETABLE_PATH + "/courses/999999"
        );
    }

    private ResultActions createTimetableRequest(int year, int semester) throws Exception {
        return performAuthenticated(post(TIMETABLE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "year", year,
                        "semester", semester
                ))));
    }

    private long createTimetable(int year, int semester) throws Exception {
        MvcResult result = createTimetableRequest(year, semester)
                .andExpect(status().isCreated())
                .andReturn();
        return responseBody(result).path("timetableId").asLong();
    }

    private ResultActions addCourseRequest(
            long timetableId,
            UUID courseId,
            String conflictPolicy
    ) throws Exception {
        Map<String, Object> request = conflictPolicy == null
                ? Map.of("courseId", courseId)
                : Map.of("courseId", courseId, "conflictPolicy", conflictPolicy);
        return performAuthenticated(post(
                        TIMETABLE_PATH + "/courses/{timetableId}",
                        timetableId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private long addCourse(
            long timetableId,
            UUID courseId,
            String conflictPolicy
    ) throws Exception {
        MvcResult result = addCourseRequest(timetableId, courseId, conflictPolicy)
                .andExpect(status().isCreated())
                .andReturn();
        return responseBody(result).path("timetableCourseId").asLong();
    }

    private ResultActions addCustomCourseRequest(
            long timetableId,
            String courseName,
            Number credit,
            String dayOfWeek,
            String startTime,
            String endTime
    ) throws Exception {
        return performAuthenticated(post(
                        TIMETABLE_PATH + "/{timetableId}/custom-courses",
                        timetableId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "courseName", courseName,
                        "credit", credit,
                        "dayOfWeek", dayOfWeek,
                        "startTime", startTime,
                        "endTime", endTime
                ))));
    }

    private long addCustomCourse(
            long timetableId,
            String courseName,
            Number credit,
            String dayOfWeek,
            String startTime,
            String endTime
    ) throws Exception {
        MvcResult result = addCustomCourseRequest(
                timetableId,
                courseName,
                credit,
                dayOfWeek,
                startTime,
                endTime
        )
                .andExpect(status().isCreated())
                .andReturn();
        return responseBody(result).path("timetableCourseId").asLong();
    }

    private ResultActions expectTimetableError(
            ResultActions resultActions,
            HttpStatus expectedStatus,
            String expectedCode,
            String expectedMessage,
            String expectedPath
    ) throws Exception {
        return resultActions
                .andExpect(status().is(expectedStatus.value()))
                .andExpect(jsonPath("$.status").value(expectedStatus.value()))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.path").value(expectedPath))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.conflicts").isArray());
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
    }

    private ResultActions performAuthenticated(MockHttpServletRequestBuilder requestBuilder)
            throws Exception {
        return mockMvc.perform(requestBuilder.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + accessToken
        ));
    }

    private AuthSession registerAndLogin(String loginId) throws Exception {
        String password = "test-password";
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "loginId", loginId,
                                "password", password
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        Long userId = responseBody(signupResult).path("userId").asLong();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "loginId", loginId,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return new AuthSession(
                userId,
                responseBody(loginResult).path("accessToken").stringValue()
        );
    }

    private void importMajorFixture() throws Exception {
        importMajorFixture(UnaryOperator.identity());
    }

    private void importTwoSectionFixture() throws Exception {
        ObjectNode payload = (ObjectNode) objectMapper.readTree(
                new ClassPathResource(SECTION_FIXTURE)
                        .getContentAsString(StandardCharsets.UTF_8)
        );
        ArrayNode lectures = (ArrayNode) payload.path("lectures");
        ObjectNode second = ((ObjectNode) lectures.get(0)).deepCopy();
        second.put("sourceRow", 6);
        second.put("sectionNo", "02");
        second.put("instructorName", "김분반");
        second.put("scheduleText", "화4,5,6");
        second.put("classroomText", "후속관 909호");
        ObjectNode schedule = (ObjectNode) second.path("schedules").get(0);
        schedule.put("dayOfWeek", "TUESDAY");
        ArrayNode periods = (ArrayNode) schedule.path("periods");
        periods.removeAll();
        periods.add(4);
        periods.add(5);
        periods.add(6);
        ObjectNode classroom = (ObjectNode) schedule.path("classroom");
        classroom.put("buildingName", "후속관");
        classroom.put("roomNumber", "909");
        classroom.put("originalValue", "후속관 909호");
        ((ObjectNode) second.path("sourceCells").get(2)).put("value", "02");
        lectures.add(second);

        ObjectNode statistics = (ObjectNode) payload.path("statistics");
        statistics.put("totalRowCount", 2);
        statistics.put("parsedLectureCount", 2);
        statistics.put("scheduleCount", 2);
        statistics.put("periodCount", 6);

        CourseImportResponse response = performImport(
                objectMapper.writeValueAsString(payload)
        );
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.STORED);
        assertThat(response.databaseChanged()).isTrue();
        assertThat(response.offeringCount()).isEqualTo(2);
    }

    private void importMajorFixture(UnaryOperator<String> modifier) throws Exception {
        CourseImportResponse response = performImport(modifier.apply(fixturePayload()));
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.STORED);
        assertThat(response.databaseChanged()).isTrue();
        assertThat(response.offeringCount()).isEqualTo(5);
    }

    private void importGeneralFixture(UnaryOperator<String> modifier) throws Exception {
        String payload = new ClassPathResource(GENERAL_FIXTURE)
                .getContentAsString(StandardCharsets.UTF_8);
        CourseImportResponse response = performImport(modifier.apply(payload));
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.STORED);
        assertThat(response.databaseChanged()).isTrue();
        assertThat(response.offeringCount()).isEqualTo(7);
    }

    private CourseImportResponse performImport(String payload) {
        return courseImportService.importCourses(
                objectMapper.readValue(payload, TimetableParseResultRequest.class)
        );
    }

    private String fixturePayload() throws Exception {
        return new ClassPathResource(FIXTURE)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private CourseOffering offeringByCode(String courseCode) {
        UUID offeringId = jdbcTemplate.queryForObject(
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
        return courseOfferingRepository.findDetailedById(offeringId).orElseThrow();
    }

    private CourseOffering offeringByCodeAndSection(String courseCode, String sectionNo) {
        UUID offeringId = jdbcTemplate.queryForObject(
                """
                select offering.id
                from course_offerings offering
                join courses course on course.id = offering.course_id
                where offering.active = true
                  and course.course_code = ?
                  and course.section_no = ?
                """,
                UUID.class,
                courseCode,
                sectionNo
        );
        return courseOfferingRepository.findDetailedById(offeringId).orElseThrow();
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

    private record AuthSession(Long userId, String accessToken) {
    }
}
