package hsu.hanseomate.domain.courseimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseExcelImportApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void majorWorkbookIsParsedStoredAndExposedByCourseQuery() throws Exception {
        MockMultipartFile file = workbookFile(
                "2026학년도 1학기 전공강좌 강의시간표.xlsx",
                majorWorkbook("웹프로그래밍", "월1,2,3")
        );

        mockMvc.perform(multipart("/api/v1/timetables/major").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.storageStatus").value("STORED"))
                .andExpect(jsonPath("$.databaseChanged").value(true))
                .andExpect(jsonPath("$.offeringCount").value(1))
                .andExpect(jsonPath("$.message").value("2026학년도 1학기 전공 강좌 저장 완료"))
                .andExpect(jsonPath("$.reviewIssues", hasSize(0)))
                .andExpect(jsonPath("$.lectures").doesNotExist());

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].courseName").value("웹프로그래밍"))
                .andExpect(jsonPath("$[0].courseCode").value("0012345"))
                .andExpect(jsonPath("$[0].schedules[0].periods", hasSize(3)));
    }

    @Test
    void xlsmWorkbookExtensionIsAccepted() throws Exception {
        MockMultipartFile file = workbookFile(
                "2026-1-major.xlsm",
                majorWorkbook("매크로 형식 강좌", "월1,2,3")
        );

        mockMvc.perform(multipart("/api/v1/timetables/major").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"))
                .andExpect(jsonPath("$.databaseChanged").value(true))
                .andExpect(jsonPath("$.offeringCount").value(1));
    }

    @Test
    void invalidGeneralWorkbookReturnsDetailedReviewAndKeepsPreviousSnapshot() throws Exception {
        MockMultipartFile valid = workbookFile(
                "2026학년도 1학기 교양강좌 강의시간표.xlsx",
                generalWorkbook("기존교양", "월1,2,3")
        );
        mockMvc.perform(multipart("/api/v1/timetables/general-education").file(valid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));

        MockMultipartFile invalid = workbookFile(
                "2026학년도 1학기 교양강좌 강의시간표-수정.xlsx",
                generalWorkbook("잘못된교양", "월2,34,5")
        );
        mockMvc.perform(multipart("/api/v1/timetables/general-education").file(invalid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.offeringCount").value(0))
                .andExpect(jsonPath("$.reviewIssues[0].code").value("INVALID_PERIOD"))
                .andExpect(jsonPath("$.reviewIssues[0].sheetName").value("2026학년도 1학기"))
                .andExpect(jsonPath("$.reviewIssues[0].rowNumber").value(4))
                .andExpect(jsonPath("$.reviewIssues[0].field").value("scheduleText"))
                .andExpect(jsonPath("$.reviewIssues[0].rawValue").value("월2,34,5"));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].courseName").value("기존교양"));
    }

    @Test
    void incompleteSingleCellGeneralLectureReturnsLocatedReviewIssue() throws Exception {
        MockMultipartFile file = workbookFile(
                "2026학년도 1학기 교양강좌 강의시간표.xlsx",
                incompleteSingleCellGeneralWorkbook()
        );

        mockMvc.perform(multipart("/api/v1/timetables/general-education").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.offeringCount").value(0))
                .andExpect(jsonPath("$.reviewIssues[0].code").value("MISSING_COURSE_CODE"))
                .andExpect(jsonPath("$.reviewIssues[0].sheetName").value("2026학년도 1학기"))
                .andExpect(jsonPath("$.reviewIssues[0].rowNumber").value(4))
                .andExpect(jsonPath("$.reviewIssues[0].field").value("courseCode"));
    }

    @Test
    void narrowlyMergedIdentityCellReturnsAmbiguousRowReview() throws Exception {
        MockMultipartFile file = workbookFile(
                "2026-1-general.xlsx",
                ambiguousMergedGeneralWorkbook()
        );

        mockMvc.perform(multipart("/api/v1/timetables/general-education").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.offeringCount").value(0))
                .andExpect(jsonPath("$.reviewIssues[0].code")
                        .value("AMBIGUOUS_MERGED_LECTURE_ROW"))
                .andExpect(jsonPath("$.reviewIssues[0].rowNumber").value(4))
                .andExpect(jsonPath("$.reviewIssues[0].field").value("courseName"));
    }

    @Test
    void mergedGeneralCategoryHeadingIsNotMistakenForLecture() throws Exception {
        MockMultipartFile file = workbookFile(
                "2026-1-general.xlsx",
                generalWorkbookWithMergedCategoryHeading()
        );

        mockMvc.perform(multipart("/api/v1/timetables/general-education").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"))
                .andExpect(jsonPath("$.databaseChanged").value(true))
                .andExpect(jsonPath("$.offeringCount").value(1))
                .andExpect(jsonPath("$.reviewIssues", hasSize(0)));
    }

    @Test
    void generalGradeRangeIsExpandedBeforeStorage() throws Exception {
        MockMultipartFile file = workbookFile(
                "2026학년도 1학기 교양강좌 강의시간표.xlsx",
                generalWorkbook("학년범위교양", "0099998", "월1,2,3", "1~4학년")
        );

        mockMvc.perform(multipart("/api/v1/timetables/general-education").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));

        assertThat(jdbcTemplate.queryForList(
                "SELECT grade FROM offering_allowed_grades ORDER BY grade",
                Integer.class
        )).containsExactly(1, 2, 3, 4);
    }

    @Test
    void shortGeneralCourseCodeIsPreservedWithoutPadding() throws Exception {
        MockMultipartFile file = workbookFile(
                "2026학년도 1학기 교양강좌 강의시간표.xlsx",
                generalWorkbook("원문코드교양", "12345", "월1,2,3", "1학년")
        );

        mockMvc.perform(multipart("/api/v1/timetables/general-education").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"))
                .andExpect(jsonPath("$.reviewIssues", hasSize(0)));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "GENERAL_EDUCATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].courseCode").value("12345"));
    }

    @Test
    void valueLongerThanDatabaseColumnReturnsLocatedReviewInsteadOfServerError() throws Exception {
        String overlongCourseName = "x".repeat(256);
        MockMultipartFile file = workbookFile(
                "2026-1-major.xlsx",
                majorWorkbook(overlongCourseName, "월1,2,3")
        );

        mockMvc.perform(multipart("/api/v1/timetables/major").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.offeringCount").value(0))
                .andExpect(jsonPath("$.reviewIssues[0].code")
                        .value("FIELD_CONSTRAINT_VIOLATION"))
                .andExpect(jsonPath("$.reviewIssues[0].rowNumber").value(3))
                .andExpect(jsonPath("$.reviewIssues[0].field")
                        .value("lectures[0].courseName"))
                .andExpect(jsonPath("$.reviewIssues[0].rawValue").value(overlongCourseName));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_offerings",
                Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_import_issues",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void mismatchedScheduleAndClassroomCountsAreNotStored() throws Exception {
        MockMultipartFile file = workbookFile(
                "2026-1-general.xlsx",
                generalWorkbook(
                        "강의실 연결 검토",
                        "0099997",
                        "월1,2 / 화3,4 / 수5,6",
                        "1학년",
                        "본관101, 본관102"
                )
        );

        mockMvc.perform(multipart("/api/v1/timetables/general-education").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.offeringCount").value(0))
                .andExpect(jsonPath("$.reviewIssues[0].code")
                        .value("SCHEDULE_CLASSROOM_COUNT_MISMATCH"))
                .andExpect(jsonPath("$.reviewIssues[0].rowNumber").value(4))
                .andExpect(jsonPath("$.reviewIssues[0].field").value("classroomText"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_offerings",
                Integer.class
        )).isZero();
    }

    @Test
    void exactSameWorkbookIsNotStoredTwice() throws Exception {
        byte[] workbook = majorWorkbook("웹프로그래밍", "월1,2,3");

        mockMvc.perform(multipart("/api/v1/timetables/major")
                        .file(workbookFile("2026학년도 1학기 전공강좌 강의시간표.xlsx", workbook)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));
        mockMvc.perform(multipart("/api/v1/timetables/major")
                        .file(workbookFile("2026학년도 1학기 전공강좌 강의시간표.xlsx", workbook)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("DUPLICATE"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.offeringCount").value(1));
    }

    @Test
    void previouslySupersededWorkbookCanBeImportedAgain() throws Exception {
        byte[] first = majorWorkbook("알파과목", "월1,2,3");
        byte[] second = majorWorkbook("베타과목", "화4,5,6");

        mockMvc.perform(multipart("/api/v1/timetables/major")
                        .file(workbookFile("2026-1-major-a.xlsx", first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));
        mockMvc.perform(multipart("/api/v1/timetables/major")
                        .file(workbookFile("2026-1-major-b.xlsx", second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"));
        mockMvc.perform(multipart("/api/v1/timetables/major")
                        .file(workbookFile("2026-1-major-a.xlsx", first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("STORED"))
                .andExpect(jsonPath("$.databaseChanged").value(true));

        mockMvc.perform(get("/api/courses")
                        .param("academicYear", "2026")
                        .param("semester", "1")
                        .param("curriculumType", "MAJOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].courseName").value("알파과목"));
    }

    @Test
    void invalidMajorCreditReturnsReviewAndDoesNotStore() throws Exception {
        MockMultipartFile file = workbookFile(
                "2026-1-major.xlsx",
                majorWorkbookWithRawGradeAndCredit("1학년", "9".repeat(400))
        );

        mockMvc.perform(multipart("/api/v1/timetables/major").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.offeringCount").value(0))
                .andExpect(jsonPath("$.reviewIssues[0].code").value("INVALID_CREDIT"))
                .andExpect(jsonPath("$.reviewIssues[0].rowNumber").value(3))
                .andExpect(jsonPath("$.reviewIssues[0].field").value("credit"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_offerings",
                Integer.class
        )).isZero();
    }

    @Test
    void invalidMajorGradeReturnsReviewAndDoesNotStore() throws Exception {
        MockMultipartFile file = workbookFile(
                "2026-1-major.xlsx",
                majorWorkbookWithRawGradeAndCredit("학년 확인 필요", "3")
        );

        mockMvc.perform(multipart("/api/v1/timetables/major").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.reviewIssues[0].code").value("INVALID_TARGET_GRADE"))
                .andExpect(jsonPath("$.reviewIssues[0].field").value("targetGrade"));
    }

    @Test
    void excessivelyLargeSchedulePeriodReturnsDetailedReview() throws Exception {
        String rawSchedule = "월999999999999999999999999";
        MockMultipartFile file = workbookFile(
                "2026-1-general.xlsx",
                generalWorkbook("교시 검토 강좌", rawSchedule)
        );

        mockMvc.perform(multipart("/api/v1/timetables/general-education").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.reviewIssues[0].code").value("INVALID_PERIOD"))
                .andExpect(jsonPath("$.reviewIssues[0].rowNumber").value(4))
                .andExpect(jsonPath("$.reviewIssues[0].field").value("scheduleText"))
                .andExpect(jsonPath("$.reviewIssues[0].rawValue").value(rawSchedule));
    }

    @Test
    void generalWorkbookSentToMajorEndpointIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/timetables/major")
                        .file(workbookFile(
                                "2026학년도 1학기 교양강좌 강의시간표.xlsx",
                                generalWorkbook("교양과목", "월1,2,3")
                        )))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("CURRICULUM_TYPE_MISMATCH"));
    }

    @Test
    void corruptWorkbookIsRejectedBeforeStorage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "2026학년도 1학기 전공강좌 강의시간표.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not-an-xlsx".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/timetables/major").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_XLSX_SIGNATURE"));
    }

    @Test
    void missingMultipartFileReturnsFileMissingError() throws Exception {
        mockMvc.perform(multipart("/api/v1/timetables/major"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_MISSING"))
                .andExpect(jsonPath("$.details.partName").value("file"));
    }

    @Test
    void emptyWorkbookFileIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/timetables/major")
                        .file(workbookFile("empty.xlsx", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
    }

    @Test
    void unsupportedWorkbookExtensionIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/timetables/major")
                        .file(workbookFile("courses.xls", new byte[]{'P', 'K'})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_EXTENSION"));
    }

    @Test
    void workbookLargerThanConfiguredUploadLimitIsRejected() throws Exception {
        byte[] oversized = new byte[(10 * 1024 * 1024) + 1];

        mockMvc.perform(multipart("/api/v1/timetables/major")
                        .file(workbookFile("oversized.xlsx", oversized)))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void workbookWithTooManySheetsIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/timetables/major")
                        .file(workbookFile("too-many-sheets.xlsx", workbookWithSheetCount(21))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("TOO_MANY_SHEETS"));
    }

    @Test
    void openApiDocumentsTheMultipartFileAsBinary() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables/major'].post.requestBody.content"
                                + "['multipart/form-data'].schema.properties.file.format"
                ).value("binary"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables/general-education'].post.requestBody.content"
                                + "['multipart/form-data'].schema.properties.file.format"
                ).value("binary"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables/major'].post.responses['200'].content"
                                + "['application/json'].schema['$ref']"
                ).value("#/components/schemas/CourseImportResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables/major'].post.responses['400'].content"
                                + "['application/json'].schema['$ref']"
                ).value("#/components/schemas/CourseWorkbookErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables/major'].post.responses['413'].content"
                                + "['application/json'].schema['$ref']"
                ).value("#/components/schemas/CourseWorkbookErrorResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables/major'].post.responses['422'].content"
                                + "['application/json'].schema['$ref']"
                ).value("#/components/schemas/CourseWorkbookErrorResponse"));
    }

    @Test
    void unsupportedHeaderWithNoParsedLectureStillReturnsDetailedReviewResult() throws Exception {
        mockMvc.perform(multipart("/api/v1/timetables/general-education")
                        .file(workbookFile(
                                "2026학년도 1학기 교양강좌 강의시간표.xlsx",
                                unsupportedGeneralWorkbook()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.storageStatus").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.databaseChanged").value(false))
                .andExpect(jsonPath("$.offeringCount").value(0))
                .andExpect(jsonPath("$.reviewIssues[0].code").value("UNSUPPORTED_GENERAL_HEADER"))
                .andExpect(jsonPath("$.reviewIssues[0].sheetName").value("2026학년도 1학기 교양"))
                .andExpect(jsonPath("$.reviewIssues[0].rowNumber").value(2));
    }

    private byte[] majorWorkbook(String courseName, String schedule) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("2026학년도 1학기 전공");
            sheet.createRow(0).createCell(0).setCellValue("항공소프트웨어공학과");
            createHeader(sheet.createRow(1));
            createLecture(sheet.createRow(2), courseName, "0012345", schedule);
            sheet.createRow(3).createCell(0).setCellValue("실용음악과(실용음악전공)");
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 9));
            sheet.createRow(4).createCell(0).setCellValue("학군단 수업");
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, 9));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithSheetCount(int sheetCount) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < sheetCount; index++) {
                workbook.createSheet("sheet-" + index);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] majorWorkbookWithRawGradeAndCredit(String grade, String credit) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("2026학년도 1학기 전공");
            sheet.createRow(0).createCell(0).setCellValue("항공소프트웨어공학과");
            createHeader(sheet.createRow(1));
            Row lecture = sheet.createRow(2);
            createLecture(lecture, "입력값 검토 강좌", "0012345", "월1,2,3");
            lecture.getCell(0).setCellValue(grade);
            lecture.getCell(4).setCellValue(credit);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] generalWorkbook(String courseName, String schedule) throws Exception {
        return generalWorkbook(courseName, "0099999", schedule, "1학년");
    }

    private byte[] generalWorkbook(
            String courseName,
            String courseCode,
            String schedule,
            String grade
    ) throws Exception {
        return generalWorkbook(courseName, courseCode, schedule, grade, "본관 101호");
    }

    private byte[] generalWorkbook(
            String courseName,
            String courseCode,
            String schedule,
            String grade,
            String classroom
    ) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("2026학년도 1학기");
            sheet.createRow(0).createCell(0).setCellValue("교양선택");
            sheet.createRow(1).createCell(0).setCellValue("1영역 탐구");
            createHeader(sheet.createRow(2));
            createLecture(sheet.createRow(3), courseName, courseCode, schedule, grade, classroom);
            sheet.createRow(4).createCell(0).setCellValue("※ 수강 안내");
            sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, 9));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] incompleteSingleCellGeneralWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("2026학년도 1학기");
            sheet.createRow(0).createCell(0).setCellValue("교양선택");
            sheet.createRow(1).createCell(0).setCellValue("1영역 탐구");
            createHeader(sheet.createRow(2));
            sheet.createRow(3).createCell(1).setCellValue("과목코드누락");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] ambiguousMergedGeneralWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("2026학년도 1학기");
            sheet.createRow(0).createCell(0).setCellValue("교양선택");
            sheet.createRow(1).createCell(0).setCellValue("1영역 사고");
            createHeader(sheet.createRow(2));
            sheet.createRow(3).createCell(1).setCellValue("병합 여부 검토 강좌");
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 1, 2));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] generalWorkbookWithMergedCategoryHeading() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("2026학년도 1학기");
            sheet.createRow(0).createCell(0).setCellValue("교양선택");
            createHeader(sheet.createRow(1));
            sheet.createRow(2).createCell(0).setCellValue("1영역(탐구)");
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 2));
            createLecture(sheet.createRow(3), "정상교양과목", "0099996", "월1,2,3");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] unsupportedGeneralWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("2026학년도 1학기 교양");
            sheet.createRow(0).createCell(0).setCellValue("교양선택");
            Row header = sheet.createRow(1);
            String[] headers = {"과목코드", "과목명", "새항목1", "새항목2", "새항목3", "새항목4"};
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("0099999");
            data.createCell(1).setCellValue("미지원형식");
            data.createCell(2).setCellValue("값1");
            data.createCell(3).setCellValue("값2");
            data.createCell(4).setCellValue("값3");
            data.createCell(5).setCellValue("값4");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void createHeader(Row row) {
        String[] headers = {
                "학년", "교과목명", "과목코드", "분반", "학점",
                "담당교수명", "요일 및 시간", "강의실", "팀티칭 여부", "비고"
        };
        for (int index = 0; index < headers.length; index++) {
            row.createCell(index).setCellValue(headers[index]);
        }
    }

    private void createLecture(Row row, String courseName, String courseCode, String schedule) {
        createLecture(row, courseName, courseCode, schedule, "1학년");
    }

    private void createLecture(
            Row row,
            String courseName,
            String courseCode,
            String schedule,
            String grade
    ) {
        createLecture(row, courseName, courseCode, schedule, grade, "본관 101호");
    }

    private void createLecture(
            Row row,
            String courseName,
            String courseCode,
            String schedule,
            String grade,
            String classroom
    ) {
        row.createCell(0).setCellValue(grade);
        row.createCell(1).setCellValue(courseName);
        row.createCell(2).setCellValue(courseCode);
        row.createCell(3).setCellValue("001");
        row.createCell(4).setCellValue(3);
        row.createCell(5).setCellValue("홍길동");
        row.createCell(6).setCellValue(schedule);
        row.createCell(7).setCellValue(classroom);
    }

    private MockMultipartFile workbookFile(String fileName, byte[] content) {
        return new MockMultipartFile(
                "file",
                fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );
    }

    private void truncate(String table) {
        jdbcTemplate.execute("TRUNCATE TABLE " + table);
    }
}
