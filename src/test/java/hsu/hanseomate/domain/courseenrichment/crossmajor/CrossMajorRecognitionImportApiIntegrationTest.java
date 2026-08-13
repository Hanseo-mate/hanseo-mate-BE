package hsu.hanseomate.domain.courseenrichment.crossmajor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.course.entity.AcademicUnit;
import hsu.hanseomate.domain.course.entity.Course;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.Semester;
import hsu.hanseomate.domain.course.repository.AcademicUnitRepository;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseRepository;
import hsu.hanseomate.domain.course.repository.SemesterRepository;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import hsu.hanseomate.domain.courseimport.repository.CourseImportHistoryRepository;
import hsu.hanseomate.support.AdminMockMvcConfiguration;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
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
class CrossMajorRecognitionImportApiIntegrationTest {

    private static final String ENDPOINT =
            "/api/admin/course-enrichments/cross-major-recognitions/imports";
    private static final String[] HEADERS = {
            "연번", "학생학부", "학생학과", "학생전공", "개설학부", "개설학과",
            "개설전공", "과목코드", "과목명", "적용년도", "적용학기"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private AcademicUnitRepository academicUnitRepository;

    @Autowired
    private CourseImportHistoryRepository courseImportHistoryRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE cross_major_recognition_rules");
            jdbcTemplate.execute("TRUNCATE TABLE cross_major_recognition_import_histories");
            jdbcTemplate.execute("TRUNCATE TABLE course_offerings");
            jdbcTemplate.execute("TRUNCATE TABLE course_import_histories");
            jdbcTemplate.execute("TRUNCATE TABLE academic_units");
            jdbcTemplate.execute("TRUNCATE TABLE courses");
            jdbcTemplate.execute("TRUNCATE TABLE semesters");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    @Test
    void importRequiresAdminRole() throws Exception {
        MockMultipartFile file = file("2026학년도 1학기.xlsx", workbook("자료구조"));

        mockMvc.perform(multipart(ENDPOINT).file(file).with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(ENDPOINT).file(file).with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_USER")
                )))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(ENDPOINT).file(file).with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));
    }

    @Test
    void sameAnnualDataInAnotherSemesterIsDuplicate() throws Exception {
        byte[] data = workbook("자료구조");

        mockMvc.perform(multipart(ENDPOINT)
                        .file(file("2026학년도 1학기 첫파일.xlsx", data))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"))
                .andExpect(jsonPath("$.databaseChanged").value(true));

        mockMvc.perform(multipart(ENDPOINT)
                        .file(file("2026학년도 2학기 재업로드.xlsx", data))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("DUPLICATE"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.uploadedSemester").value(2));

        assertThat(count("cross_major_recognition_import_histories")).isEqualTo(1);
        assertThat(count("cross_major_recognition_rules")).isEqualTo(1);
    }

    @Test
    void changedAnnualDataAtomicallyReplacesActiveHistory() throws Exception {
        importAsAdmin("2026학년도 1학기.xlsx", workbook("자료구조"));

        mockMvc.perform(multipart(ENDPOINT)
                        .file(file("2026학년도 2학기.xlsx", workbook("고급자료구조")))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"))
                .andExpect(jsonPath("$.databaseChanged").value(true));

        assertThat(count("cross_major_recognition_import_histories")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cross_major_recognition_import_histories WHERE status='ACTIVE'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cross_major_recognition_import_histories WHERE status='SUPERSEDED'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT course_name_snapshot FROM cross_major_recognition_rules r "
                        + "JOIN cross_major_recognition_import_histories h "
                        + "ON h.id=r.import_history_id WHERE h.status='ACTIVE'",
                String.class
        )).isEqualTo("고급자료구조");
    }

    @Test
    void reviewRequiredHistoryDoesNotChangeCurrentActiveData() throws Exception {
        importAsAdmin("2026학년도 1학기.xlsx", workbook("자료구조"));

        mockMvc.perform(multipart(ENDPOINT)
                        .file(file("2026학년도 2학기 오류.xlsx", invalidWorkbook()))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.ruleCount").value(0))
                .andExpect(jsonPath("$.reviewIssues[0].severity").value("ERROR"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cross_major_recognition_import_histories WHERE status='ACTIVE'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cross_major_recognition_import_histories "
                        + "WHERE status='REVIEW_REQUIRED'",
                Integer.class
        )).isEqualTo(1);
        assertThat(count("cross_major_recognition_rules")).isEqualTo(1);
    }

    @Test
    void invalidOfficeSignatureIsRejectedBeforeParsing() throws Exception {
        MockMultipartFile file = file(
                "2026학년도 1학기.xlsx",
                "not-an-xlsx".getBytes()
        );

        mockMvc.perform(multipart(ENDPOINT)
                        .file(file)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_XLSX_SIGNATURE"));

        assertThat(count("cross_major_recognition_import_histories")).isZero();
    }

    @Test
    void emptyAndUnsupportedFilesAreRejectedBeforeParsing() throws Exception {
        mockMvc.perform(multipart(ENDPOINT)
                        .file(file("2026학년도 1학기.xlsx", new byte[0]))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));

        mockMvc.perform(multipart(ENDPOINT)
                        .file(file("2026학년도 1학기.xls", new byte[]{'P', 'K'}))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_EXTENSION"));

        assertThat(count("cross_major_recognition_import_histories")).isZero();
    }

    @Test
    void fileOverTenMebibytesIsRejectedBeforeParsing() throws Exception {
        byte[] oversized = new byte[(10 * 1024 * 1024) + 1];

        mockMvc.perform(multipart(ENDPOINT)
                        .file(file("2026학년도 1학기.xlsx", oversized))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        assertThat(count("cross_major_recognition_import_histories")).isZero();
    }

    @Test
    void courseDetailMatchesRecognitionByCourseNameDespiteDifferentCodeAndOrganization()
            throws Exception {
        CourseOffering offering = courseOffering();
        importAsAdmin("2026학년도 1학기.xlsx", workbook("자료구조"));

        mockMvc.perform(get("/api/courses/{offeringId}", offering.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crossMajorRecognitions.length()").value(1))
                .andExpect(jsonPath("$.crossMajorRecognitions[0]").value("학생학과"));
    }

    private void importAsAdmin(String name, byte[] content) throws Exception {
        mockMvc.perform(multipart(ENDPOINT)
                        .file(file(name, content))
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));
    }

    private CourseOffering courseOffering() {
        Semester semester = semesterRepository.save(Semester.create(2026, 1));
        Course course = courseRepository.save(Course.create(
                "cross-major-course",
                "9999999",
                "자료 구조"
        ));
        AcademicUnit academicUnit = academicUnitRepository.save(AcademicUnit.create(
                "cross-major-unit",
                "다른학과 다른전공",
                "다른학과",
                "다른전공"
        ));
        CourseImportHistory importHistory = courseImportHistoryRepository.save(
                CourseImportHistory.stored(
                        "cross-major-import",
                        "cross-major-dedup",
                        "major.xlsx",
                        "a".repeat(64),
                        "1.0",
                        "test",
                        2026,
                        1,
                        CurriculumType.MAJOR,
                        "전공 강좌",
                        BigDecimal.ONE,
                        1,
                        "{}"
                )
        );
        return courseOfferingRepository.save(CourseOffering.create(
                semester,
                course,
                academicUnit,
                importHistory,
                CurriculumType.MAJOR,
                "테스트",
                1,
                "9999999",
                "자료 구조",
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

    private byte[] workbook(String courseName) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("타학과 전공인정 교과목 리스트");
            writeHeaders(sheet.createRow(0));
            writeRule(sheet.createRow(1), "공과대학", "학생학과", "학생전공",
                    "공과대학", "개설학과", "개설전공", "0004436", courseName, 2020, 1);
            workbook.createSheet("Sheet1").createRow(0).createCell(0).setCellValue("#REF!");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] invalidWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("타학과 전공인정 교과목 리스트");
            writeHeaders(sheet.createRow(0));
            writeRule(sheet.createRow(1), "", "학생학과", "학생전공",
                    "공과대학", "개설학과", "개설전공", "ABC", "자료구조", 2020, 1);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void writeHeaders(Row row) {
        for (int index = 0; index < HEADERS.length; index++) {
            row.createCell(index).setCellValue(HEADERS[index]);
        }
    }

    private void writeRule(
            Row row,
            String studentCollege,
            String studentDepartment,
            String studentMajor,
            String offeringCollege,
            String offeringDepartment,
            String offeringMajor,
            String courseCode,
            String courseName,
            int effectiveYear,
            int effectiveSemester
    ) {
        row.createCell(0).setCellValue(1);
        row.createCell(1).setCellValue(studentCollege);
        row.createCell(2).setCellValue(studentDepartment);
        row.createCell(3).setCellValue(studentMajor);
        row.createCell(4).setCellValue(offeringCollege);
        row.createCell(5).setCellValue(offeringDepartment);
        row.createCell(6).setCellValue(offeringMajor);
        row.createCell(7).setCellValue(courseCode);
        row.createCell(8).setCellValue(courseName);
        row.createCell(9).setCellValue(effectiveYear);
        row.createCell(10).setCellValue(effectiveSemester);
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile(
                "file",
                name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
