package hsu.hanseomate.domain.courseimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSourceCell;
import hsu.hanseomate.domain.course.entity.Semester;
import hsu.hanseomate.domain.course.entity.SemesterGeneralCategoryNode;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseSourceCellRepository;
import hsu.hanseomate.domain.course.repository.SemesterGeneralCategoryNodeRepository;
import hsu.hanseomate.domain.course.repository.SemesterRepository;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import hsu.hanseomate.domain.courseimport.repository.CourseImportHistoryRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

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

        performImport("/api/internal/course-imports/major", payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importId").value("major-2026-1-a"))
                .andExpect(jsonPath("$.storageStatus").value("STORED"))
                .andExpect(jsonPath("$.databaseChanged").value(true))
                .andExpect(jsonPath("$.offeringCount").value(1))
                .andExpect(jsonPath("$.message").value("2026학년도 1학기 전공 강좌 저장 완료"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseCode").value("001234"))
                .andExpect(jsonPath("$[0].sectionNo").value("01"))
                .andExpect(jsonPath("$[0].courseName").value("웹프로그래밍"))
                .andExpect(jsonPath("$[0].academicUnit.departmentName")
                        .value("항공소프트웨어공학과"))
                .andExpect(jsonPath("$[0].schedules[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$[0].schedules[0].periods[0]").value(1))
                .andExpect(jsonPath("$[0].schedules[0].periods[1]").value(2))
                .andExpect(jsonPath("$[0].schedules[0].periods[2]").value(3))
                .andExpect(jsonPath("$[0].schedules[0].classroom.originalValue")
                        .value("본관 101호"))
                .andExpect(jsonPath("$[0].sourceCells").doesNotExist())
                .andExpect(jsonPath("$[0].fileSha256").doesNotExist());

        CourseOffering offering = courseOfferingRepository.findAll().get(0);
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

        performImport("/api/internal/course-imports/major", payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));

        CourseImportHistory history = courseImportHistoryRepository.findByImportId("major-large-payload")
                .orElseThrow();
        assertThat(history.getRawPayloadJson().getBytes(StandardCharsets.UTF_8).length)
                .isGreaterThan(65_535);
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

        performImport("/api/internal/course-imports/major", payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));

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
        performImport(
                "/api/internal/course-imports/major",
                fixture("major-ready-2026-1-a.json")
        ).andExpect(status().isOk());

        long offeringCount = courseOfferingRepository.count();
        long historyCount = courseImportHistoryRepository.count();

        performImport(
                "/api/internal/course-imports/major",
                fixture("major-review-required-2026-1.json")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importId").value("major-2026-1-review"))
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.offeringCount").value(0));

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
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("웹프로그래밍"));
    }

    @Test
    void readyImportWithPeriod111IsDefensivelyRejected() throws Exception {
        performImport(
                "/api/internal/course-imports/major",
                fixture("major-ready-invalid-period.json")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.offeringCount").value(0));

        assertThat(courseOfferingRepository.count()).isZero();
        assertThat(courseImportHistoryRepository.count()).isEqualTo(1);
        CourseImportHistory rejected = courseImportHistoryRepository.findAll().get(0);
        assertThat(rejected.getStorageStatus().name()).isEqualTo("REVIEW_REQUIRED");
        assertThat(rejected.getRawPayloadJson()).contains("111교시", "[111]");
    }

    @Test
    void successfulFileRetryIsIdempotent() throws Exception {
        String original = fixture("major-ready-2026-1-a.json");
        performImport("/api/internal/course-imports/major", original)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));

        String retry = original.replace(
                "\"importId\": \"major-2026-1-a\"",
                "\"importId\": \"major-2026-1-a-retry\""
        );
        performImport("/api/internal/course-imports/major", retry)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importId").value("major-2026-1-a-retry"))
                .andExpect(jsonPath("$.storageStatus").value("DUPLICATE"))
                .andExpect(jsonPath("$.databaseChanged").value(false));

        assertThat(courseOfferingRepository.count()).isEqualTo(1);
        assertThat(courseImportHistoryRepository.count()).isEqualTo(1);
    }

    @Test
    void databaseConstraintFailureRollsBackTheWholeSnapshotReplacement() throws Exception {
        performImport(
                "/api/internal/course-imports/major",
                fixture("major-ready-2026-1-a.json")
        ).andExpect(status().isOk());

        String invalidReplacement = fixture("major-ready-2026-1-b.json")
                .replace("\"credit\": 3.0", "\"credit\": 999999999.0");

        performImport("/api/internal/course-imports/major", invalidReplacement)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));

        assertThat(courseOfferingRepository.count()).isEqualTo(1);
        assertThat(courseImportHistoryRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("웹프로그래밍"));
    }

    @Test
    void reimportReplacesOnlyMatchingSemesterAndCurriculum() throws Exception {
        performImport("/api/internal/course-imports/major", fixture("major-ready-2026-1-a.json"));
        performImport(
                "/api/internal/course-imports/general-education",
                fixture("general-ready-2026-1-ocu.json")
        );
        performImport("/api/internal/course-imports/major", fixture("major-ready-2025-2.json"));
        performImport("/api/internal/course-imports/major", fixture("major-ready-2026-1-b.json"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.courseName == '웹프로그래밍')]").isEmpty())
                .andExpect(jsonPath("$[?(@.courseName == '서버프로그래밍')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.courseName == 'OCU디지털리터러시')]").isNotEmpty());

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2025")
                        .param("semester", "2")
                        .param("curriculumType", "MAJOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("이전학기강좌"));

        assertThat(courseOfferingRepository.count()).isEqualTo(3);
    }

    @Test
    void generalTaxonomyReplacementRemovesOcuAndExposesSdu() throws Exception {
        performImport(
                "/api/internal/course-imports/general-education",
                fixture("general-ready-2026-1-ocu.json")
        ).andExpect(status().isOk());
        performImport(
                "/api/internal/course-imports/general-education",
                fixture("general-ready-2026-1-sdu.json")
        ).andExpect(status().isOk());

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
                        .param("deliveryProvider", "OCU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("deliveryProvider", "SDU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("SDU미래사회"))
                .andExpect(jsonPath("$[0].generalEducation.deliveryProviderName").value("SDU"));
    }

    @Test
    void userCourseQuerySupportsMajorAndGeneralEducationFilters() throws Exception {
        performImport("/api/internal/course-imports/major", fixture("major-ready-2026-1-a.json"));
        performImport(
                "/api/internal/course-imports/general-education",
                fixture("general-ready-2026-1-sdu.json")
        );

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR")
                        .param("academicUnit", "소프트웨어")
                        .param("courseName", "웹")
                        .param("instructorName", "홍"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseCode").value("001234"))
                .andExpect(jsonPath("$[0].schedules[0].periods.length()").value(3));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION")
                        .param("classification", "ELECTIVE")
                        .param("area", "OTHER")
                        .param("deliveryProvider", "SDU")
                        .param("courseName", "미래")
                        .param("instructorName", "이교수"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].curriculumType").value("GENERAL_EDUCATION"))
                .andExpect(jsonPath("$[0].generalEducation.classification").value("ELECTIVE"))
                .andExpect(jsonPath("$[0].generalEducation.deliveryProvider").value("SDU"))
                .andExpect(jsonPath("$[0].schedules[0].periods[0]").value(7))
                .andExpect(jsonPath("$[0].schedules[0].periods[1]").value(8));
    }

    private ResultActions performImport(String path, String payload) throws Exception {
        TimetableParseResultRequest request = readRequest(payload);
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-IMPORT-ID", request.importId())
                .header("X-PARSER-SCHEMA-VERSION", request.schemaVersion())
                .header("Idempotency-Key", idempotencyKey(request))
                .content(payload));
    }

    private TimetableParseResultRequest readRequest(String payload) {
        return objectMapper.readValue(payload, TimetableParseResultRequest.class);
    }

    private String idempotencyKey(TimetableParseResultRequest request) {
        return "%d:%d:%s:%s".formatted(
                request.academicYear(),
                request.semester(),
                request.curriculumType(),
                request.fileSha256()
        );
    }

    private String fixture(String name) throws Exception {
        return new ClassPathResource(FIXTURE_ROOT + name)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private void truncate(String table) {
        jdbcTemplate.execute("TRUNCATE TABLE " + table);
    }
}
