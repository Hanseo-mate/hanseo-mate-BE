package hsu.hanseomate.domain.courseimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.course.entity.Course;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSourceCell;
import hsu.hanseomate.domain.course.entity.Semester;
import hsu.hanseomate.domain.course.entity.SemesterGeneralCategoryNode;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseRepository;
import hsu.hanseomate.domain.course.repository.CourseSourceCellRepository;
import hsu.hanseomate.domain.course.repository.SemesterGeneralCategoryNodeRepository;
import hsu.hanseomate.domain.course.repository.SemesterRepository;
import hsu.hanseomate.domain.courseimport.dto.CourseImportResponse;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import hsu.hanseomate.domain.courseimport.repository.CourseImportHistoryRepository;
import hsu.hanseomate.domain.courseimport.service.CourseImportService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseImportApiIntegrationTest {

    private static final String FIXTURE_ROOT = "fixtures/course-import/";
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseImportService courseImportService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSourceCellRepository courseSourceCellRepository;

    @Autowired
    private CourseImportHistoryRepository courseImportHistoryRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private SemesterGeneralCategoryNodeRepository generalCategoryNodeRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
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

    @Test
    void storesReadyMajorImportAndPreservesOriginalData() throws Exception {
        String payload = fixture("major-ready-2026-1-a.json");

        CourseImportResponse response = performImport(payload);
        assertThat(response.importId()).isEqualTo("major-2026-1-a");
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.STORED);
        assertThat(response.databaseChanged()).isTrue();
        assertThat(response.offeringCount()).isEqualTo(1);
        assertThat(response.reviewIssues()).isEmpty();
        assertThat(response.message()).isEqualTo("2026학년도 1학기 전공 강좌 저장 완료");

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.items[0].courseName").value("웹프로그래밍"))
                .andExpect(jsonPath("$.items[0].credit").value(3))
                .andExpect(jsonPath("$.items[0].cyber").value(false))
                .andExpect(jsonPath("$.items[0].originalAcademicUnitName")
                        .value("항공소프트웨어공학과"))
                .andExpect(jsonPath("$.items[0].generalCategory").doesNotExist())
                .andExpect(jsonPath("$.items[0].schedules[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.items[0].schedules[0].periods[0]").value(1))
                .andExpect(jsonPath("$.items[0].schedules[0].periods[1]").value(2))
                .andExpect(jsonPath("$.items[0].schedules[0].periods[2]").value(3))
                .andExpect(jsonPath("$.items[0].schedules[0].buildingName").value("본관"))
                .andExpect(jsonPath("$.items[0].schedules[0].roomNumber").value("101"))
                .andExpect(jsonPath("$.items[0].note").doesNotExist())
                .andExpect(jsonPath("$.items[0].equivalentCourses").doesNotExist())
                .andExpect(jsonPath("$.items[0].crossMajorRecognitions").doesNotExist())
                .andExpect(jsonPath("$.items[0].courseCode").doesNotExist())
                .andExpect(jsonPath("$.items[0].academicUnit").doesNotExist())
                .andExpect(jsonPath("$.items[0].generalEducation").doesNotExist())
                .andExpect(jsonPath("$.items[0].commonGrade").doesNotExist())
                .andExpect(jsonPath("$.items[0].sourceCells").doesNotExist())
                .andExpect(jsonPath("$.items[0].fileSha256").doesNotExist());

        CourseOffering offering = firstActiveOffering();
        assertThat(offering.getSectionNo()).isEqualTo("01");
        assertThat(offering.getTeamTeaching()).isNull();
        assertThat(offering.getScheduleText()).isEqualTo("월1,2,3");
        assertThat(offering.getClassroomText()).isEqualTo("본관 101호");

        CourseSourceCell unknownCell = courseSourceCellRepository.findAll().stream()
                .filter(cell -> cell.getColumnIndex() == 4)
                .findFirst()
                .orElseThrow();
        assertThat(unknownCell.getHeaderName()).isEqualTo("새로 생긴 열");
        assertThat(unknownCell.getCanonicalField()).isNull();
        assertThat(unknownCell.getValue()).isNull();

        CourseImportHistory history = courseImportHistoryRepository.findAll().get(0);
        JsonNode rawPayload = objectMapper.readTree(history.getRawPayloadJson());
        JsonNode rawUnknownCell = rawPayload.path("lectures").get(0)
                .path("sourceCells").get(3);
        assertThat(rawUnknownCell.path("headerName").asString()).isEqualTo("새로 생긴 열");
        assertThat(rawUnknownCell.path("canonicalField").isNull()).isTrue();
        assertThat(rawUnknownCell.path("value").isNull()).isTrue();
    }

    @Test
    void courseDetailReturnsMajorCourseDisplayInformation() throws Exception {
        String payload = fixture("major-ready-2026-1-a.json")
                .replace("\"note\": null", "\"note\": \"온라인수업\"");
        performImport(payload);
        CourseOffering offering = firstActiveOffering();

        var result = mockMvc.perform(get("/api/courses/{offeringId}", offering.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeringId").value(offering.getId().toString()))
                .andExpect(jsonPath("$.courseName").value(offering.getCourseName()))
                .andExpect(jsonPath("$.sectionNo").value(offering.getSectionNo()))
                .andExpect(jsonPath("$.credit").value(3))
                .andExpect(jsonPath("$.cyber").value(true))
                .andExpect(jsonPath("$.instructorName").isNotEmpty())
                .andExpect(jsonPath("$.curriculumType").value("MAJOR"))
                .andExpect(jsonPath("$.targetGrade").value(2))
                .andExpect(jsonPath("$.originalAcademicUnitName")
                        .value("항공소프트웨어공학과"))
                .andExpect(jsonPath("$.generalCategory").doesNotExist())
                .andExpect(jsonPath("$.schedules[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.schedules[0].periods[0]").value(1))
                .andExpect(jsonPath("$.schedules[0].periods[1]").value(2))
                .andExpect(jsonPath("$.schedules[0].periods[2]").value(3))
                .andExpect(jsonPath("$.schedules[0].buildingName").isNotEmpty())
                .andExpect(jsonPath("$.schedules[0].roomNumber").value("101"))
                .andExpect(jsonPath("$.note").value("온라인수업"))
                .andExpect(jsonPath("$.equivalentCourses").isArray())
                .andExpect(jsonPath("$.equivalentCourses").isEmpty())
                .andExpect(jsonPath("$.crossMajorRecognitions").isArray())
                .andExpect(jsonPath("$.crossMajorRecognitions").isEmpty())
                .andExpect(jsonPath("$.courseCode").doesNotExist())
                .andExpect(jsonPath("$.commonGrade").doesNotExist())
                .andExpect(jsonPath("$.academicUnit").doesNotExist())
                .andExpect(jsonPath("$.generalEducation").doesNotExist())
                .andReturn();

        JsonNode detail = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(detail.path("credit").isIntegralNumber()).isTrue();
    }

    @Test
    void courseListAndDetailReturnGeneralEducationClassification() throws Exception {
        performImport(fixture("course-search-general-2026-1.json"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION")
                        .param("generalCategories", "REQUIRED")
                        .param("sort", "COURSE_CODE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].curriculumType")
                        .value("GENERAL_EDUCATION"))
                .andExpect(jsonPath("$.items[0].generalCategory").value("REQUIRED"))
                .andExpect(jsonPath("$.items[0].originalAcademicUnitName").doesNotExist())
                .andExpect(jsonPath("$.items[0].schedules[0].periods[0]").value(0))
                .andExpect(jsonPath("$.items[0].schedules[0].periods[1]").value(1))
                .andExpect(jsonPath("$.items[0].note").doesNotExist());

        CourseOffering ocuOffering = activeOfferingByCode("105000");

        mockMvc.perform(get("/api/courses/{offeringId}", ocuOffering.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.curriculumType").value("GENERAL_EDUCATION"))
                .andExpect(jsonPath("$.cyber").value(true))
                .andExpect(jsonPath("$.originalAcademicUnitName").doesNotExist())
                .andExpect(jsonPath("$.generalCategory").value("OCU"))
                .andExpect(jsonPath("$.note").value("OCU 수강 안내"))
                .andExpect(jsonPath("$.generalEducation").doesNotExist());
    }

    @Test
    void unknownCourseDetailReturnsNotFound() throws Exception {
        UUID unknownOfferingId = UUID.randomUUID();

        mockMvc.perform(get("/api/courses/{offeringId}", unknownOfferingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("강좌를 찾을 수 없습니다. offeringId=" + unknownOfferingId))
                .andExpect(jsonPath("$.path")
                        .value("/api/courses/" + unknownOfferingId));
    }

    @Test
    void storesRawPayloadLargerThanMysqlTextLimit() throws Exception {
        String warningMessage = "x".repeat(1500);
        String issues = IntStream.range(0, 80)
                .mapToObj(index -> """
                        {
                          "severity": "WARNING",
                          "code": "LARGE_PAYLOAD_WARNING",
                          "message": "%s",
                          "sheetName": "전공",
                          "rowNumber": 5,
                          "field": null,
                          "rawValue": null
                        }
                        """.formatted(warningMessage))
                .collect(Collectors.joining(","));

        String payload = fixture("major-ready-2026-1-a.json")
                .replace("\"importId\": \"major-2026-1-a\"", "\"importId\": \"major-large-payload\"")
                .replace(
                        "\"fileSha256\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
                        "\"fileSha256\": \"eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\""
                )
                .replace("\"issues\": []", "\"issues\": [" + issues + "]");

        assertThat(payload.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(65_535);

        assertThat(performImport(payload).storageStatus()).isEqualTo(StorageStatus.STORED);

        CourseImportHistory history = courseImportHistoryRepository.findByImportId("major-large-payload")
                .orElseThrow();
        assertThat(history.getRawPayloadJson().getBytes(StandardCharsets.UTF_8).length)
                .isGreaterThan(65_535);
    }

    @Test
    void excessiveReviewIssuesAreCappedInResponseAndDatabase() throws Exception {
        String issues = IntStream.range(0, 1100)
                .mapToObj(index -> """
                        {
                          "severity": "ERROR",
                          "code": "ROW_REVIEW_REQUIRED",
                          "message": "검토가 필요한 행입니다.",
                          "sheetName": "전공",
                          "rowNumber": %d,
                          "field": "courseName",
                          "rawValue": "값"
                        }
                        """.formatted(index + 1))
                .collect(Collectors.joining(","));
        String payload = fixture("major-ready-2026-1-a.json")
                .replace("\"importId\": \"major-2026-1-a\"", "\"importId\": \"major-many-issues\"")
                .replace(
                        "\"fileSha256\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
                        "\"fileSha256\": \"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd\""
                )
                .replace("\"issues\": []", "\"issues\": [" + issues + "]");

        CourseImportResponse response = performImport(payload);

        assertThat(response.storageStatus()).isEqualTo(StorageStatus.REVIEW_REQUIRED);
        assertThat(response.databaseChanged()).isFalse();
        assertThat(response.reviewIssues()).hasSize(1000);
        assertThat(response.reviewIssues().get(999).code()).isEqualTo("ISSUES_TRUNCATED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_import_issues",
                Integer.class
        )).isEqualTo(1000);
    }

    @Test
    void storesSourceCellValueLargerThanMysqlTextLimit() throws Exception {
        String largeCellValue = "x".repeat(70_000);
        String payload = fixture("major-ready-2026-1-a.json")
                .replace("\"importId\": \"major-2026-1-a\"", "\"importId\": \"major-large-source-cell\"")
                .replace(
                        "\"fileSha256\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
                        "\"fileSha256\": \"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff\""
                )
                .replace("\"value\": null", "\"value\": \"" + largeCellValue + "\"");

        assertThat(largeCellValue.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(65_535);

        assertThat(performImport(payload).storageStatus()).isEqualTo(StorageStatus.STORED);

        CourseSourceCell savedCell = courseSourceCellRepository.findAll().stream()
                .filter(cell -> cell.getColumnIndex() == 4)
                .findFirst()
                .orElseThrow();
        assertThat(savedCell.getValue()).isEqualTo(largeCellValue);
    }

    @Test
    void importIssueTableAvoidsMysqlRowNumberReservedWord() {
        Integer safeColumnCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where lower(table_name) = 'course_import_issues'
                  and lower(column_name) = 'issue_row_number'
                """,
                Integer.class
        );
        Integer reservedColumnCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where lower(table_name) = 'course_import_issues'
                  and lower(column_name) = 'row_number'
                """,
                Integer.class
        );

        assertThat(safeColumnCount).isEqualTo(1);
        assertThat(reservedColumnCount).isZero();
    }

    @Test
    void reviewRequiredImportDoesNotChangeServiceData() throws Exception {
        performImport(fixture("major-ready-2026-1-a.json"));

        long offeringCount = courseOfferingRepository.count();
        long historyCount = courseImportHistoryRepository.count();

        CourseImportResponse response = performImport(
                fixture("major-review-required-2026-1.json")
        );
        assertThat(response.importId()).isEqualTo("major-2026-1-review");
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.REVIEW_REQUIRED);
        assertThat(response.databaseChanged()).isFalse();
        assertThat(response.offeringCount()).isZero();
        assertThat(response.reviewIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("AMBIGUOUS_HEADING_HIERARCHY");
            assertThat(issue.rowNumber()).isEqualTo(182);
            assertThat(issue.field()).isEqualTo("scheduleText");
        });

        assertThat(courseOfferingRepository.count()).isEqualTo(offeringCount);
        assertThat(courseImportHistoryRepository.count()).isEqualTo(historyCount + 1);
        assertThat(courseImportHistoryRepository.findAll())
                .anySatisfy(history -> {
                    assertThat(history.getImportId()).isEqualTo("major-2026-1-review");
                    assertThat(history.getStorageStatus().name()).isEqualTo("REVIEW_REQUIRED");
                    assertThat(history.getSuccessfulDedupKey()).isNull();
                });
        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("웹프로그래밍"));
    }

    @Test
    void readyImportWithPeriod111IsDefensivelyRejected() throws Exception {
        CourseImportResponse response = performImport(
                fixture("major-ready-invalid-period.json")
        );
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.REVIEW_REQUIRED);
        assertThat(response.databaseChanged()).isFalse();
        assertThat(response.offeringCount()).isZero();
        assertThat(response.reviewIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("INVALID_PERIOD");
            assertThat(issue.rowNumber()).isEqualTo(182);
            assertThat(issue.field()).isEqualTo("scheduleText");
            assertThat(issue.rawValue()).isEqualTo("111");
        });

        assertThat(courseOfferingRepository.count()).isZero();
        assertThat(courseImportHistoryRepository.count()).isEqualTo(1);
        CourseImportHistory rejected = courseImportHistoryRepository.findAll().get(0);
        assertThat(rejected.getStorageStatus().name()).isEqualTo("REVIEW_REQUIRED");
        assertThat(rejected.getRawPayloadJson()).contains("111교시", "[111]");
        Integer savedReviewIssueCount = jdbcTemplate.queryForObject(
                "select count(*) from course_import_issues where code = 'INVALID_PERIOD'",
                Integer.class
        );
        assertThat(savedReviewIssueCount).isEqualTo(1);
    }

    @Test
    void successfulFileRetryIsIdempotent() throws Exception {
        String original = fixture("major-ready-2026-1-a.json");
        assertThat(performImport(original).storageStatus()).isEqualTo(StorageStatus.STORED);

        String retry = original.replace(
                "\"importId\": \"major-2026-1-a\"",
                "\"importId\": \"major-2026-1-a-retry\""
        );
        CourseImportResponse response = performImport(retry);
        assertThat(response.importId()).isEqualTo("major-2026-1-a-retry");
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.DUPLICATE);
        assertThat(response.databaseChanged()).isFalse();

        assertThat(courseOfferingRepository.count()).isEqualTo(1);
        assertThat(courseImportHistoryRepository.count()).isEqualTo(1);
    }

    @Test
    void sameCourseCodeWithDifferentSectionsCreatesIndependentOfferings() throws Exception {
        ObjectNode payload = (ObjectNode) objectMapper.readTree(
                fixture("major-ready-2026-1-a.json")
        );
        ArrayNode lectures = (ArrayNode) payload.path("lectures");
        ObjectNode duplicate = ((ObjectNode) lectures.get(0)).deepCopy();
        duplicate.put("sourceRow", 6);
        duplicate.put("courseCode", " 001234 ");
        duplicate.put("sectionNo", "99");
        duplicate.put("instructorName", "김분반");
        duplicate.put("scheduleText", "화4,5,6");
        duplicate.put("classroomText", "후속관 909호");
        duplicate.put("targetGrade", 4);
        ArrayNode duplicateAllowedGrades = (ArrayNode) duplicate.path("allowedGrades");
        duplicateAllowedGrades.removeAll();
        duplicateAllowedGrades.add(4);
        ArrayNode duplicateEligibleDepartments =
                (ArrayNode) duplicate.path("eligibleDepartmentNames");
        duplicateEligibleDepartments.removeAll();
        duplicateEligibleDepartments.add("후속 중복 학과");
        ObjectNode duplicateSchedule = (ObjectNode) duplicate.path("schedules").get(0);
        duplicateSchedule.put("dayOfWeek", "TUESDAY");
        ArrayNode duplicatePeriods = (ArrayNode) duplicateSchedule.path("periods");
        duplicatePeriods.removeAll();
        duplicatePeriods.add(4);
        duplicatePeriods.add(5);
        duplicatePeriods.add(6);
        ObjectNode duplicateClassroom = (ObjectNode) duplicateSchedule.path("classroom");
        duplicateClassroom.put("buildingName", "후속관");
        duplicateClassroom.put("roomNumber", "909");
        duplicateClassroom.put("originalValue", "후속관 909호");
        ((ObjectNode) duplicate.path("sourceCells").get(0))
                .put("value", " 001234 ");
        ((ObjectNode) duplicate.path("sourceCells").get(1))
                .put("value", "웹프로그래밍");
        ((ObjectNode) duplicate.path("sourceCells").get(2))
                .put("value", "99");
        lectures.add(duplicate);

        ObjectNode statistics = (ObjectNode) payload.path("statistics");
        statistics.put("totalRowCount", 2);
        statistics.put("parsedLectureCount", 2);
        statistics.put("scheduleCount", 2);
        statistics.put("periodCount", 6);

        CourseImportResponse response = performImport(
                objectMapper.writeValueAsString(payload)
        );

        assertThat(response.storageStatus()).isEqualTo(StorageStatus.STORED);
        assertThat(response.offeringCount()).isEqualTo(2);
        assertThat(courseRepository.count()).isEqualTo(2);
        assertThat(courseOfferingRepository.count()).isEqualTo(2);
        assertThat(tableCount("course_schedules")).isEqualTo(2);
        assertThat(tableCount("offering_allowed_grades")).isEqualTo(2);
        assertThat(tableCount("offering_eligible_departments")).isEqualTo(2);

        assertThat(courseRepository.findAll())
                .allSatisfy(course -> {
                    assertThat(course.getCourseCode()).isEqualTo("001234");
                    assertThat(course.getCourseName()).isEqualTo("웹프로그래밍");
                })
                .extracting(Course::getSectionNo)
                .containsExactlyInAnyOrder("01", "99");
        assertThat(courseRepository.findAll())
                .filteredOn(course -> "01".equals(course.getSectionNo()))
                .singleElement()
                .satisfies(course -> {
                    assertThat(course.getInstructorName()).isEqualTo("홍길동");
                    assertThat(course.getTargetGrade()).isEqualTo(2);
                    assertThat(course.getScheduleText()).isEqualTo("월1,2,3");
                    assertThat(course.getClassroomText()).isEqualTo("본관 101호");
                });
        assertThat(courseRepository.findAll())
                .filteredOn(course -> "99".equals(course.getSectionNo()))
                .singleElement()
                .satisfies(course -> {
                    assertThat(course.getInstructorName()).isEqualTo("김분반");
                    assertThat(course.getTargetGrade()).isEqualTo(4);
                    assertThat(course.getScheduleText()).isEqualTo("화4,5,6");
                    assertThat(course.getClassroomText()).isEqualTo("후속관 909호");
                });

        assertThat(jdbcTemplate.queryForList(
                "select grade from offering_allowed_grades",
                Integer.class
        )).containsExactlyInAnyOrder(2, 4);
        assertThat(jdbcTemplate.queryForList(
                "select department_name from offering_eligible_departments",
                String.class
        )).containsExactlyInAnyOrder("항공소프트웨어공학과", "후속 중복 학과");
        assertThat(tableCount("classrooms")).isEqualTo(2);

        assertThat(courseOfferingRepository.findAll())
                .extracting(CourseOffering::getSourceRow)
                .containsExactlyInAnyOrder(5, 6);
        assertThat(courseSourceCellRepository.findAll())
                .hasSize(8)
                .extracting(CourseSourceCell::getValue)
                .contains("웹프로그래밍", "01", "99");

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].courseName").value("웹프로그래밍"))
                .andExpect(jsonPath("$.items[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.items[0].targetGrade").value(2))
                .andExpect(jsonPath("$.items[0].eligibleDepartmentNames[0]")
                        .value("항공소프트웨어공학과"))
                .andExpect(jsonPath("$.items[0].schedules[0].dayOfWeek")
                        .value("MONDAY"))
                .andExpect(jsonPath("$.items[0].schedules[0].periods[0]").value(1))
                .andExpect(jsonPath("$.items[0].schedules[0].periods[2]").value(3))
                .andExpect(jsonPath("$.items[0].schedules[0].buildingName").value("본관"))
                .andExpect(jsonPath("$.items[0].schedules[0].roomNumber").value("101"))
                .andExpect(jsonPath("$.items[1].courseName").value("웹프로그래밍"))
                .andExpect(jsonPath("$.items[1].sectionNo").value("99"))
                .andExpect(jsonPath("$.items[1].instructorName").value("김분반"))
                .andExpect(jsonPath("$.items[1].targetGrade").value(4))
                .andExpect(jsonPath("$.items[1].eligibleDepartmentNames[0]")
                        .value("후속 중복 학과"))
                .andExpect(jsonPath("$.items[1].schedules[0].dayOfWeek")
                        .value("TUESDAY"))
                .andExpect(jsonPath("$.items[1].schedules[0].periods[0]").value(4))
                .andExpect(jsonPath("$.items[1].schedules[0].periods[2]").value(6))
                .andExpect(jsonPath("$.items[1].schedules[0].buildingName").value("후속관"))
                .andExpect(jsonPath("$.items[1].schedules[0].roomNumber").value("909"));

        CourseOffering secondSectionOffering = courseOfferingRepository.findAll().stream()
                .filter(offering -> "99".equals(offering.getSectionNo()))
                .findFirst()
                .orElseThrow();
        mockMvc.perform(get("/api/courses/{offeringId}", secondSectionOffering.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeringId")
                        .value(secondSectionOffering.getId().toString()))
                .andExpect(jsonPath("$.courseName").value("웹프로그래밍"))
                .andExpect(jsonPath("$.sectionNo").value("99"))
                .andExpect(jsonPath("$.instructorName").value("김분반"))
                .andExpect(jsonPath("$.schedules[0].dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$.schedules[0].periods[0]").value(4))
                .andExpect(jsonPath("$.schedules[0].buildingName").value("후속관"))
                .andExpect(jsonPath("$.schedules[0].roomNumber").value("909"));

        CourseImportHistory history = courseImportHistoryRepository.findAll().get(0);
        assertThat(history.getOfferingCount()).isEqualTo(2);
        JsonNode rawLectures = objectMapper.readTree(history.getRawPayloadJson())
                .path("lectures");
        assertThat(rawLectures.size()).isEqualTo(2);
        assertThat(rawLectures.get(1).path("sectionNo").asString()).isEqualTo("99");
        assertThat(rawLectures.get(1).path("schedules").get(0)
                .path("dayOfWeek").asString()).isEqualTo("TUESDAY");
        assertThat(rawLectures.get(1).path("allowedGrades").get(0).asInt()).isEqualTo(4);
        assertThat(rawLectures.get(1).path("eligibleDepartmentNames").get(0).asString())
                .isEqualTo("후속 중복 학과");
    }

    @Test
    void invalidLaterRowWithSameCourseCodeRequiresReviewAndStoresNoCourseData()
            throws Exception {
        ObjectNode payload = (ObjectNode) objectMapper.readTree(
                fixture("major-ready-2026-1-a.json")
        );
        ArrayNode lectures = (ArrayNode) payload.path("lectures");
        ObjectNode invalidDuplicate = ((ObjectNode) lectures.get(0)).deepCopy();
        invalidDuplicate.put("sourceRow", 6);
        invalidDuplicate.put("credit", 999_999_999.0);
        lectures.add(invalidDuplicate);

        ObjectNode statistics = (ObjectNode) payload.path("statistics");
        statistics.put("totalRowCount", 2);
        statistics.put("parsedLectureCount", 2);
        statistics.put("scheduleCount", 2);
        statistics.put("periodCount", 6);

        CourseImportResponse response = performImport(
                objectMapper.writeValueAsString(payload)
        );

        assertThat(response.storageStatus()).isEqualTo(StorageStatus.REVIEW_REQUIRED);
        assertThat(response.databaseChanged()).isFalse();
        assertThat(response.offeringCount()).isZero();
        assertThat(response.reviewIssues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("INVALID_CREDIT");
            assertThat(issue.rowNumber()).isEqualTo(6);
            assertThat(issue.field()).isEqualTo("credit");
        });

        assertThat(courseRepository.count()).isZero();
        assertThat(courseOfferingRepository.count()).isZero();
        assertThat(courseSourceCellRepository.count()).isZero();
        assertThat(tableCount("course_schedules")).isZero();
        assertThat(tableCount("offering_allowed_grades")).isZero();
        assertThat(tableCount("offering_eligible_departments")).isZero();

        List<CourseImportHistory> histories = courseImportHistoryRepository.findAll();
        assertThat(histories).singleElement().satisfies(history -> {
            assertThat(history.getStorageStatus()).isEqualTo(StorageStatus.REVIEW_REQUIRED);
            assertThat(history.getOfferingCount()).isZero();
        });
        assertThat(objectMapper.readTree(histories.get(0).getRawPayloadJson())
                .path("lectures").size()).isEqualTo(2);
    }

    @Test
    void sameCourseCodeAcrossSemestersSharesCourseAndDetailsButCreatesTermOfferings()
            throws Exception {
        assertThat(performImport(fixture("major-ready-2026-1-a.json")).storageStatus())
                .isEqualTo(StorageStatus.STORED);
        assertThat(performImport(majorFixtureForSemester2(
                "major-2026-2-same-course"
        )).storageStatus()).isEqualTo(StorageStatus.STORED);

        assertThat(courseRepository.count()).isEqualTo(1);
        assertThat(courseOfferingRepository.findAll())
                .hasSize(2)
                .allMatch(CourseOffering::isActive);
        assertThat(jdbcTemplate.queryForObject(
                "select count(distinct course_id) from course_offerings",
                Integer.class
        )).isEqualTo(1);
        assertThat(tableCount("course_schedules")).isEqualTo(1);
        assertThat(tableCount("offering_allowed_grades")).isEqualTo(1);
        assertThat(tableCount("offering_eligible_departments")).isEqualTo(1);

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("웹프로그래밍"));
        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("웹프로그래밍"));
    }

    @Test
    void sameCourseNameWithDifferentCodesCreatesDifferentCourses() throws Exception {
        assertThat(performImport(fixture("major-ready-2026-1-a.json")).storageStatus())
                .isEqualTo(StorageStatus.STORED);
        String differentCode = majorFixtureForSemester2("major-2026-2-different-code")
                .replace(
                        "\"courseCode\": \"001234\"",
                        "\"courseCode\": \"009999\""
                )
                .replace("\"value\": \"001234\"", "\"value\": \"009999\"");

        assertThat(performImport(differentCode).storageStatus()).isEqualTo(StorageStatus.STORED);

        List<Course> courses = courseRepository.findAll();
        assertThat(courses).hasSize(2);
        assertThat(courses).extracting(Course::getCourseCode)
                .containsExactlyInAnyOrder("001234", "009999");
        assertThat(courses).extracting(Course::getCourseName)
                .containsOnly("웹프로그래밍");
        assertThat(courseOfferingRepository.findAll())
                .hasSize(2)
                .allMatch(CourseOffering::isActive);
    }

    @Test
    void sameCourseNameWithoutCodeCreatesDifferentCourses() throws Exception {
        String first = fixture("major-ready-2026-1-a.json")
                .replace("\"courseCode\": \"001234\"", "\"courseCode\": null")
                .replace("\"value\": \"001234\"", "\"value\": null");
        String second = majorFixtureForSemester2("major-2026-2-no-course-code")
                .replace("\"courseCode\": \"001234\"", "\"courseCode\": null")
                .replace("\"value\": \"001234\"", "\"value\": null");

        assertThat(performImport(first).storageStatus()).isEqualTo(StorageStatus.STORED);
        assertThat(performImport(second).storageStatus()).isEqualTo(StorageStatus.STORED);

        List<Course> courses = courseRepository.findAll();
        assertThat(courses).hasSize(2);
        assertThat(courses).extracting(Course::getCourseCode).containsOnlyNulls();
        assertThat(courses).extracting(Course::getCourseName)
                .containsOnly("웹프로그래밍");
        assertThat(courses).extracting(Course::getMasterKey).doesNotHaveDuplicates();
        assertThat(courseOfferingRepository.findAll())
                .hasSize(2)
                .allMatch(CourseOffering::isActive);
    }

    @Test
    void laterImportWithSameCourseCodeAndSectionKeepsFirstStoredDetails() throws Exception {
        assertThat(performImport(fixture("major-ready-2026-1-a.json")).storageStatus())
                .isEqualTo(StorageStatus.STORED);
        String changedDetails = majorFixtureForSemester2("major-2026-2-changed-details")
                .replace("웹프로그래밍", "후속수입과목명")
                .replace("\"credit\": 3.0", "\"credit\": 4.0")
                .replace("\"instructorName\": \"홍길동\"", "\"instructorName\": \"후속교수\"")
                .replace("\"note\": null", "\"note\": \"후속 비고\"");

        assertThat(performImport(changedDetails).storageStatus()).isEqualTo(StorageStatus.STORED);

        assertThat(courseRepository.findAll()).singleElement().satisfies(course -> {
            assertThat(course.getCourseCode()).isEqualTo("001234");
            assertThat(course.getCourseName()).isEqualTo("웹프로그래밍");
            assertThat(course.getSectionNo()).isEqualTo("01");
            assertThat(course.getCredit()).isEqualByComparingTo("3.0");
            assertThat(course.getInstructorName()).isEqualTo("홍길동");
            assertThat(course.getNote()).isNull();
        });
        assertThat(courseOfferingRepository.findAll())
                .hasSize(2)
                .allMatch(CourseOffering::isActive);
        assertThat(tableCount("course_schedules")).isEqualTo(1);

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("웹프로그래밍"))
                .andExpect(jsonPath("$.items[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.items[0].credit").value(3))
                .andExpect(jsonPath("$.items[0].instructorName").value("홍길동"));
    }

    @Test
    void oversizedCreditReturnsLocatedReviewAndKeepsTheWholeSnapshot() throws Exception {
        performImport(fixture("major-ready-2026-1-a.json"));

        String invalidReplacement = fixture("major-ready-2026-1-b.json")
                .replace("\"credit\": 3.0", "\"credit\": 999999999.0");

        CourseImportResponse response = performImport(invalidReplacement);
        assertThat(response.storageStatus()).isEqualTo(StorageStatus.REVIEW_REQUIRED);
        assertThat(response.databaseChanged()).isFalse();
        assertThat(response.offeringCount()).isZero();
        assertThat(response.reviewIssues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("INVALID_CREDIT");
            assertThat(issue.rowNumber()).isEqualTo(7);
            assertThat(issue.field()).isEqualTo("credit");
            assertThat(issue.rawValue()).isEqualTo("9.99999999E8");
        });

        assertThat(courseOfferingRepository.count()).isEqualTo(1);
        assertThat(courseImportHistoryRepository.count()).isEqualTo(2);
        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("웹프로그래밍"));
    }

    @Test
    void reimportDeactivatesMissingMappingsOnlyWithinMatchingSemesterAndCurriculum()
            throws Exception {
        performImport(fixture("major-ready-2026-1-a.json"));
        performImport(fixture("general-ready-2026-1-ocu.json"));
        performImport(fixture("major-ready-2025-2.json"));
        performImport(fixture("major-ready-2026-1-b.json"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.courseName == '웹프로그래밍')]").isEmpty())
                .andExpect(jsonPath("$.items[?(@.courseName == '서버프로그래밍')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.courseName == 'OCU디지털리터러시')]").isNotEmpty());

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2025")
                        .param("semester", "2")
                        .param("curriculumType", "MAJOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("이전학기강좌"));

        List<CourseOffering> offerings = courseOfferingRepository.findAll();
        assertThat(offerings).hasSize(4);
        assertThat(jdbcTemplate.queryForObject(
                """
                select offering.active
                from course_offerings offering
                join courses course on course.id = offering.course_id
                where course.course_code = ?
                """,
                Boolean.class,
                "001234"
        )).isFalse();
        assertThat(offerings.stream().filter(CourseOffering::isActive)).hasSize(3);
    }

    @Test
    void generalTaxonomyReplacementRemovesOcuAndExposesSdu() throws Exception {
        performImport(fixture("general-ready-2026-1-ocu.json"));
        performImport(fixture("general-ready-2026-1-sdu.json"));

        Semester semester = semesterRepository.findByAcademicYearAndSemester(2026, 1)
                .orElseThrow();
        List<SemesterGeneralCategoryNode> nodes = generalCategoryNodeRepository.findByScope(
                semester.getId(), CurriculumType.GENERAL_EDUCATION
        );
        assertThat(nodes).hasSize(5);
        assertThat(nodes).extracting(SemesterGeneralCategoryNode::getNodeKey)
                .contains("provider-sdu", "category-no-course")
                .doesNotContain("provider-ocu");

        Map<String, SemesterGeneralCategoryNode> byKey = nodes.stream()
                .collect(Collectors.toMap(
                        SemesterGeneralCategoryNode::getNodeKey,
                        Function.identity()
                ));
        assertThat(byKey.get("provider-sdu").getParentKey()).isEqualTo("area-other");
        assertThat(byKey.get("area-other").getParentKey()).isEqualTo("category-remote");
        assertThat(byKey.get("category-remote").getParentKey())
                .isEqualTo("classification-elective");
        assertThat(objectMapper.readTree(byKey.get("provider-sdu").getSourcePathJson()).size())
                .isEqualTo(4);

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION")
                        .param("generalCategories", "OCU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION")
                        .param("generalCategories", "SDU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("SDU미래사회"))
                .andExpect(jsonPath("$.items[0].cyber").value(true))
                .andExpect(jsonPath("$.items[0].generalCategory").value("SDU"));
    }

    @Test
    void academicUnitsAreOrWithinGroupAndDifferentFilterGroupsAreAnded() throws Exception {
        performImport(fixture("course-search-major-2026-1.json"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("academicUnits", "항공소프트웨어공학과", "항공운항학과")
                        .param("searchField", "INSTRUCTOR_NAME")
                        .param("keyword", "교수")
                        .param("sort", "COURSE_CODE")
                        .param("startPeriod", "2")
                        .param("endPeriod", "5")
                        .param("grades", "GRADE_2", "GRADE_3")
                        .param("credits", "CREDIT_2", "CREDIT_3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].courseName").value("찰리실습"))
                .andExpect(jsonPath("$.items[1].courseName").value("베타연구"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void academicYearSemesterAndSingleAcademicUnitFilterAreAppliedTogether() throws Exception {
        performImport(fixture("major-ready-2025-2.json"));
        String targetSemester = fixture("course-search-major-2026-1.json")
                .replace("course-search-major-2026-1", "course-search-major-2026-2")
                .replace(
                        "1111111111111111111111111111111111111111111111111111111111111111",
                        "2222222222222222222222222222222222222222222222222222222222222222"
                )
                .replace("\"semester\": 1", "\"semester\": 2");
        performImport(targetSemester);

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "2")
                        .param("curriculumType", "MAJOR")
                        .param("academicUnits", "항공소프트웨어공학과"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath(
                        "$.items[?(@.originalAcademicUnitName != '항공소프트웨어공학과')]"
                ).isEmpty());

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "2")
                        .param("curriculumType", "MAJOR")
                        .param("academicUnits", "존재하지않는학과"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void keywordSearchTargetsOnlyTheSelectedCourseField() throws Exception {
        performImport(fixture("course-search-major-2026-1.json"));

        assertFieldSearch("COURSE_NAME", "알파", "알파개론");
        assertFieldSearch("INSTRUCTOR_NAME", "김교수", "알파개론");
        assertFieldSearch("COURSE_CODE", "003000", "알파개론");
        assertFieldSearch("LOCATION", "본관 101", "알파개론");

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("keyword", "김교수"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void courseSearchSupportsDefaultCourseCodeAndCourseNameSorts() throws Exception {
        performImport(fixture("course-search-major-2026-1.json"));

        assertSortedCourseNames(
                "DEFAULT",
                "알파개론", "찰리실습", "베타연구", "델타프로젝트", "기타세미나"
        );
        assertSortedCourseNames(
                "COURSE_CODE",
                "찰리실습", "베타연구", "알파개론", "델타프로젝트", "기타세미나"
        );
        assertSortedCourseNames(
                "COURSE_NAME",
                "기타세미나", "델타프로젝트", "베타연구", "알파개론", "찰리실습"
        );
    }

    @Test
    void timeRangeIncludesOnlyCoursesWhoseEveryScheduleIsFullyInsideTheRange() throws Exception {
        performImport(fixture("course-search-major-2026-1.json"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("startPeriod", "0")
                        .param("endPeriod", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("알파개론"))
                .andExpect(jsonPath("$.items[0].schedules[0].periods[0]").value(0));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("startPeriod", "1")
                        .param("endPeriod", "5")
                        .param("sort", "COURSE_CODE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].courseName").value("찰리실습"))
                .andExpect(jsonPath("$.items[1].courseName").value("베타연구"))
                .andExpect(jsonPath(
                        "$.items[?(@.courseName == '기타세미나')]"
                ).isEmpty());

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("startPeriod", "0")
                        .param("endPeriod", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void gradesAndCreditsAreOrWithinEachGroupAndAndedBetweenGroups() throws Exception {
        performImport(fixture("course-search-major-2026-1.json"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("grades", "GRADE_1", "GRADE_3")
                        .param("credits", "CREDIT_1", "CREDIT_3")
                        .param("sort", "DEFAULT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].courseName").value("알파개론"))
                .andExpect(jsonPath("$.items[1].courseName").value("베타연구"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("grades", "OTHER")
                        .param("credits", "CREDIT_4_OR_MORE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("기타세미나"));
    }

    @Test
    void selectingAllGradesAndCreditsIsEquivalentToNotSelectingThoseFilters() throws Exception {
        performImport(fixture("course-search-major-2026-1.json"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param(
                                "grades",
                                "GRADE_1", "GRADE_2", "GRADE_3", "GRADE_4", "OTHER"
                        )
                        .param(
                                "credits",
                                "CREDIT_1", "CREDIT_2", "CREDIT_3", "CREDIT_4_OR_MORE"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void generalEducationCategoriesAreOrWithinTheSameFilterGroup() throws Exception {
        performImport(fixture("course-search-general-2026-1.json"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION")
                        .param("generalCategories", "REQUIRED", "AREA_1", "OCU")
                        .param("sort", "COURSE_CODE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].courseName").value("필수인성"))
                .andExpect(jsonPath("$.items[0].cyber").value(false))
                .andExpect(jsonPath("$.items[0].generalCategory").value("REQUIRED"))
                .andExpect(jsonPath("$.items[1].courseName").value("탐구사고"))
                .andExpect(jsonPath("$.items[1].cyber").value(false))
                .andExpect(jsonPath("$.items[1].generalCategory").value("AREA_1"))
                .andExpect(jsonPath("$.items[2].courseName").value("OCU디지털"))
                .andExpect(jsonPath("$.items[2].cyber").value(true))
                .andExpect(jsonPath("$.items[2].generalCategory").value("OCU"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION")
                        .param("generalCategories", "OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("기타원격"))
                .andExpect(jsonPath("$.items[0].cyber").value(false))
                .andExpect(jsonPath("$.items[0].generalCategory").value("OTHER"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION")
                        .param("generalCategories", "AREA_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("탐구사고"))
                .andExpect(jsonPath("$.items[0].generalCategory").value("AREA_1"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION")
                        .param("generalCategories", "OCU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value("OCU디지털"))
                .andExpect(jsonPath("$.items[0].generalCategory").value("OCU"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION")
                        .param(
                                "generalCategories",
                                "REQUIRED", "AREA_1", "AREA_2", "AREA_3", "E_CLASS",
                                "HSU_CYBER", "OCU", "CHUNGNAM_ELEARNING", "SDU", "OTHER"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(7))
                .andExpect(jsonPath("$.totalElements").value(7));
    }

    @Test
    void courseSearchWithoutConditionsReturnsEveryStoredCourse() throws Exception {
        performImport(fixture("course-search-major-2026-1.json"));
        performImport(fixture("course-search-general-2026-1.json"));

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(12))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void courseSearchPaginationKeepsStableSortAndMetadata() throws Exception {
        performImport(fixture("course-search-major-2026-1.json"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("sort", "COURSE_CODE")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].courseName").value("찰리실습"))
                .andExpect(jsonPath("$.items[1].courseName").value("베타연구"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("sort", "COURSE_CODE")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].courseName").value("알파개론"))
                .andExpect(jsonPath("$.items[1].courseName").value("델타프로젝트"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void invalidEverytimeCourseSearchParametersReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/courses").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses")
                        .param("startPeriod", "1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses")
                        .param("startPeriod", "3")
                        .param("endPeriod", "2"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses")
                        .param("startPeriod", "-1")
                        .param("endPeriod", "2"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses")
                        .param("startPeriod", "0")
                        .param("endPeriod", "31"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses").param("searchField", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses").param("sort", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses").param("grades", "GRADE_5"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses").param("credits", "CREDIT_5"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/courses").param("generalCategories", "AREA_4"))
                .andExpect(status().isBadRequest());
    }

    private void assertFieldSearch(
            String searchField,
            String keyword,
            String expectedCourseName
    ) throws Exception {
        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("searchField", searchField)
                        .param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].courseName").value(expectedCourseName));
    }

    private void assertSortedCourseNames(String sort, String... expectedNames) throws Exception {
        var result = mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("sort", sort))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("items");
        assertThat(items.size()).isEqualTo(expectedNames.length);
        for (int index = 0; index < expectedNames.length; index++) {
            assertThat(items.get(index).path("courseName").stringValue())
                    .isEqualTo(expectedNames[index]);
        }
    }

    private CourseImportResponse performImport(String payload) {
        return courseImportService.importCourses(readRequest(payload));
    }

    private TimetableParseResultRequest readRequest(String payload) {
        return objectMapper.readValue(payload, TimetableParseResultRequest.class);
    }

    private String fixture(String name) throws Exception {
        return new ClassPathResource(FIXTURE_ROOT + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private String majorFixtureForSemester2(String importId) throws Exception {
        return fixture("major-ready-2026-1-a.json")
                .replace("\"importId\": \"major-2026-1-a\"", "\"importId\": \"" + importId + "\"")
                .replace("\"semester\": 1", "\"semester\": 2")
                .replace("2026학년도 1학기", "2026학년도 2학기");
    }

    private CourseOffering firstActiveOffering() {
        UUID offeringId = jdbcTemplate.queryForObject(
                "select id from course_offerings where active = true limit 1",
                UUID.class
        );
        return courseOfferingRepository.findDetailedById(offeringId).orElseThrow();
    }

    private CourseOffering activeOfferingByCode(String courseCode) {
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

    private int tableCount(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private void truncate(String table) {
        jdbcTemplate.execute("TRUNCATE TABLE " + table);
    }
}
