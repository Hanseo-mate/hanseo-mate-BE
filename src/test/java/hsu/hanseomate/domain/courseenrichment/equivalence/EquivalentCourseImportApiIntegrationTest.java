package hsu.hanseomate.domain.courseenrichment.equivalence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.course.entity.Course;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.Semester;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseRepository;
import hsu.hanseomate.domain.course.repository.SemesterRepository;
import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseHistoryStatus;
import hsu.hanseomate.domain.courseenrichment.equivalence.repository.EquivalentCourseImportHistoryRepository;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import hsu.hanseomate.domain.courseimport.repository.CourseImportHistoryRepository;
import hsu.hanseomate.support.AdminMockMvcConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminMockMvcConfiguration.class)
class EquivalentCourseImportApiIntegrationTest {

    private static final String PATH =
            "/api/admin/course-enrichments/equivalent-courses/imports";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EquivalentCourseImportHistoryRepository historyRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseImportHistoryRepository courseImportHistoryRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            jdbcTemplate.execute("DELETE FROM equivalent_course_members");
            jdbcTemplate.execute("DELETE FROM equivalent_course_groups");
            jdbcTemplate.execute("DELETE FROM equivalent_course_import_histories");
            jdbcTemplate.execute("DELETE FROM course_offerings");
            jdbcTemplate.execute("DELETE FROM course_import_histories");
            jdbcTemplate.execute("DELETE FROM courses");
            jdbcTemplate.execute("DELETE FROM semesters");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    @Test
    void importRequiresAdminRole() throws Exception {
        MockMultipartFile file = file(
                "2026-2 동일교과목현황.xlsx",
                List.of(
                        row("1", "0000001", "첫 과목"),
                        row("", "0000002", "둘째 과목")
                )
        );

        mockMvc.perform(multipart(PATH).file(file).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(PATH).file(file).with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_USER")
                )))
                .andExpect(status().isForbidden());
    }

    @Test
    void storesDuplicateReviewsAndAtomicallyReplacesScope() throws Exception {
        mockMvc.perform(multipart(PATH).file(file(
                        "2026-2 최초.xlsx",
                        List.of(
                                row("1", "0000001", "알고리즘"),
                                row("", "0000002", "알고리즘 응용")
                        )
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"))
                .andExpect(jsonPath("$.databaseChanged").value(true))
                .andExpect(jsonPath("$.groupCount").value(1))
                .andExpect(jsonPath("$.memberCount").value(2));

        mockMvc.perform(multipart(PATH).file(file(
                        "2026-2 순서변경.xlsx",
                        List.of(
                                row("99", "0000002", "알고리즘 응용"),
                                row("", "0000001", "알고리즘")
                        )
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("DUPLICATE"))
                .andExpect(jsonPath("$.databaseChanged").value(false));

        mockMvc.perform(multipart(PATH).file(file(
                        "2026-2 검토필요.xlsx",
                        List.of(
                                row("1", "0000001", "첫 과목"),
                                row("2", "0000002", "둘째 과목"),
                                row("1", "0000003", "셋째 과목")
                        )
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.issues[*].code")
                        .value(hasItem("NON_CONTIGUOUS_SERIAL_REAPPEARED")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equivalent_course_import_histories "
                        + "WHERE active_scope_key = '2026:2'",
                Integer.class
        )).isEqualTo(1);

        mockMvc.perform(multipart(PATH).file(file(
                        "2026-2 변경.xlsx",
                        List.of(
                                row("1", "0000001", "알고리즘"),
                                row("", "0000003", "알고리즘 특론")
                        )
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"))
                .andExpect(jsonPath("$.databaseChanged").value(true));

        assertThat(historyRepository.count()).isEqualTo(3);
        Map<EquivalentCourseHistoryStatus, Long> statusCounts = historyRepository.findAll()
                .stream()
                .collect(Collectors.groupingBy(
                        history -> history.getHistoryStatus(),
                        Collectors.counting()
                ));
        assertThat(statusCounts).containsExactlyInAnyOrderEntriesOf(Map.of(
                EquivalentCourseHistoryStatus.ACTIVE, 1L,
                EquivalentCourseHistoryStatus.SUPERSEDED, 1L,
                EquivalentCourseHistoryStatus.REVIEW_REQUIRED, 1L
        ));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equivalent_course_members",
                Integer.class
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equivalent_course_import_histories "
                        + "WHERE active_scope_key = '2026:2'",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void courseDetailReturnsOtherMembersInSourceOrder() throws Exception {
        CourseOffering offering = courseOffering("0000002", "알고리즘 응용");
        mockMvc.perform(multipart(PATH).file(file(
                        "2026-2 동일교과목현황.xlsx",
                        List.of(
                                row("1", "0000001", "알고리즘"),
                                row("", "0000002", "알고리즘 응용"),
                                row("", "0000003", "알고리즘 특론")
                        )
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));

        mockMvc.perform(get("/api/courses/{offeringId}", offering.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equivalentCourses.length()").value(2))
                .andExpect(jsonPath("$.equivalentCourses[0].courseCode").value("0000001"))
                .andExpect(jsonPath("$.equivalentCourses[0].courseName").value("알고리즘"))
                .andExpect(jsonPath("$.equivalentCourses[1].courseCode").value("0000003"))
                .andExpect(jsonPath("$.equivalentCourses[1].courseName").value("알고리즘 특론"));
    }

    @Test
    void openApiDocumentsBothImportsAndCourseDetailEnrichments() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/course-enrichments/equivalent-courses/imports']"
                                + ".post.requestBody.content['multipart/form-data']"
                                + ".schema.properties.file.format"
                ).value("binary"))
                .andExpect(jsonPath(
                        "$.paths['/api/admin/course-enrichments/cross-major-recognitions/imports']"
                                + ".post.requestBody.content['multipart/form-data']"
                                + ".schema.properties.file.format"
                ).value("binary"))
                .andExpect(jsonPath(
                        "$.paths['/api/admin/course-enrichments/equivalent-courses/imports']"
                                + ".post.responses['200'].content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/EquivalentCourseImportResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/admin/course-enrichments/cross-major-recognitions/imports']"
                                + ".post.responses['200'].content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/CrossMajorRecognitionImportResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.CourseOfferingDetailResponse.properties"
                                + ".equivalentCourses.type"
                ).value("array"))
                .andExpect(jsonPath(
                        "$.components.schemas.CourseOfferingDetailResponse.properties"
                                + ".crossMajorRecognitions.type"
                ).value("array"));
    }

    private CourseOffering courseOffering(String code, String name) {
        Semester semester = semesterRepository.save(Semester.create(2026, 2));
        Course course = courseRepository.save(Course.create("test-" + code, code, name));
        CourseImportHistory importHistory = courseImportHistoryRepository.save(
                CourseImportHistory.stored(
                        "test-import-" + code,
                        "test-dedup-" + code,
                        "test.xlsx",
                        "a".repeat(64),
                        "1.0",
                        "test",
                        2026,
                        2,
                        CurriculumType.MAJOR,
                        "테스트 강좌",
                        BigDecimal.ONE,
                        1,
                        "{}"
                )
        );
        return courseOfferingRepository.save(CourseOffering.create(
                semester,
                course,
                null,
                importHistory,
                CurriculumType.MAJOR,
                "테스트",
                1,
                code,
                name,
                "001",
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(3),
                "교수",
                1,
                false,
                false,
                null,
                null,
                null,
                null
        ));
    }

    private static MockMultipartFile file(String name, List<String[]> rows) throws IOException {
        return new MockMultipartFile(
                "file",
                name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbook(rows)
        );
    }

    private static String[] row(String serial, String code, String name) {
        return new String[]{serial, code, name};
    }

    private static byte[] workbook(List<String[]> values) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("sheet 1");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("일련번호");
            header.createCell(2).setCellValue("과목코드");
            header.createCell(3).setCellValue("과목명");
            for (int index = 0; index < values.size(); index++) {
                String[] value = values.get(index);
                Row row = sheet.createRow(index + 2);
                if (!value[0].isEmpty()) {
                    row.createCell(0).setCellValue(value[0]);
                }
                row.createCell(2).setCellValue(value[1]);
                row.createCell(3).setCellValue(value[2]);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
