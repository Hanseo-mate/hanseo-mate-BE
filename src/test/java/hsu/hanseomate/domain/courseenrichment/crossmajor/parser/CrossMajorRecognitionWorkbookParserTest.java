package hsu.hanseomate.domain.courseenrichment.crossmajor.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionParseResult;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class CrossMajorRecognitionWorkbookParserTest {

    private static final String SOURCE_SHEET = "타학과 전공인정 교과목 리스트";
    private static final String[] HEADERS = {
            "연번", "학생학부", "학생학과", "학생전공", "개설학부", "개설학과",
            "개설전공", "과목코드", "과목명", "적용년도", "적용학기"
    };

    private final CrossMajorRecognitionWorkbookParser parser =
            new CrossMajorRecognitionWorkbookParser();

    @Test
    void parsesBThroughKPadsCodesDeduplicatesAndIgnoresBrokenSheet() throws Exception {
        byte[] workbook = workbook(
                null,
                SOURCE_SHEET,
                List.of(
                        rule("공과대학", "A학과", "A전공", "공과대학", "B학과", "B전공",
                                4436, "자료구조", 2020, 1),
                        rule("공과대학", "A학과", "A전공", "공과대학", "B학과", "B전공",
                                "0004436", "자료구조", "2020", "1학기"),
                        rule("공과대학", "C학과", "C전공", "공과대학", "B학과", "B전공",
                                "1234567", "운영체제", 2026, 2)
                ),
                true
        );

        CrossMajorRecognitionParseResult result = parser.parse(
                workbook,
                "2026학년도 1학기 타학과 전공인정 교과목 리스트(26.06.02.).xlsx"
        );

        assertThat(result.policyYear()).isEqualTo(2026);
        assertThat(result.uploadedSemester()).isEqualTo(1);
        assertThat(result.sourceSheet()).isEqualTo(SOURCE_SHEET);
        assertThat(result.rawRowCount()).isEqualTo(3);
        assertThat(result.rules()).hasSize(2);
        assertThat(result.rules().get(0).courseCode()).isEqualTo("0004436");
        assertThat(result.rules().get(1).effectiveSemester()).isEqualTo(2);
        assertThat(result.issues()).anyMatch(issue -> "DUPLICATE_RULE".equals(issue.code()));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void detectsScopeOnlyFromFileTitleOrSheetMetadata() throws Exception {
        byte[] workbook = workbook(
                "2026학년도 1학기 전공인정 정책",
                SOURCE_SHEET,
                List.<Object[]>of(rule("대학", "학생과", "학생전공", "대학", "개설과", "개설전공",
                        "1234567", "과목", 2005, 2)),
                false
        );

        CrossMajorRecognitionParseResult result = parser.parse(workbook, "recognitions.xlsx");

        assertThat(result.policyYear()).isEqualTo(2026);
        assertThat(result.uploadedSemester()).isEqualTo(1);
        assertThat(result.rules().get(0).effectiveYear()).isEqualTo(2005);
        assertThat(result.rules().get(0).effectiveSemester()).isEqualTo(2);
    }

    @Test
    void rejectsConflictingMetadataScopes() throws Exception {
        byte[] workbook = workbook(
                "2026학년도 1학기 전공인정 정책",
                "2026학년도 2학기 " + SOURCE_SHEET,
                List.<Object[]>of(rule("대학", "학생과", "학생전공", "대학", "개설과", "개설전공",
                        "1234567", "과목", 2020, 1)),
                false
        );

        assertThatThrownBy(() -> parser.parse(workbook, "recognitions.xlsx"))
                .isInstanceOfSatisfying(CourseWorkbookParseException.class,
                        exception -> assertThat(exception.code()).isEqualTo("SEMESTER_CONFLICT"));
    }

    @Test
    void doesNotTreatShortDateInFileNameAsPolicyScope() throws Exception {
        byte[] workbook = workbook(
                null,
                SOURCE_SHEET,
                List.<Object[]>of(rule("대학", "학생과", "학생전공", "대학", "개설과", "개설전공",
                        "1234567", "과목", 2020, 1)),
                false
        );

        assertThatThrownBy(() -> parser.parse(
                workbook,
                "타학과 전공인정 교과목 리스트(26.06.02.).xlsx"
        )).isInstanceOfSatisfying(CourseWorkbookParseException.class,
                exception -> assertThat(exception.code()).isEqualTo("SEMESTER_NOT_FOUND"));
    }

    @Test
    void invalidRequiredCodeYearAndSemesterBecomeReviewErrors() throws Exception {
        byte[] workbook = workbook(
                null,
                SOURCE_SHEET,
                List.<Object[]>of(rule("", "학생과", "학생전공", "대학", "개설과", "개설전공",
                        "ABC", "과목", 1999, 3)),
                false
        );

        CrossMajorRecognitionParseResult result = parser.parse(
                workbook,
                "2026학년도 1학기.xlsx"
        );

        assertThat(result.rules()).isEmpty();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.issues()).extracting("code").contains(
                "MISSING_REQUIRED_VALUE",
                "INVALID_COURSE_CODE",
                "INVALID_EFFECTIVE_YEAR",
                "INVALID_EFFECTIVE_SEMESTER"
        );
    }

    @Test
    void canonicalHashExcludesFileNameUploadedSemesterOrderAndDuplicates() throws Exception {
        Object[] first = rule("대학", "학생과1", "전공1", "대학", "개설과", "개설전공",
                "123", "과목A", 2020, 1);
        Object[] second = rule("대학", "학생과2", "전공2", "대학", "개설과", "개설전공",
                "456", "과목B", 2021, 2);
        byte[] semesterOne = workbook(null, SOURCE_SHEET, List.of(first, second), false);
        byte[] semesterTwo = workbook(null, SOURCE_SHEET, List.of(second, first, first), false);

        CrossMajorRecognitionParseResult firstResult = parser.parse(
                semesterOne,
                "2026학년도 1학기 첫파일.xlsx"
        );
        CrossMajorRecognitionParseResult secondResult = parser.parse(
                semesterTwo,
                "2026학년도 2학기 다른파일.xlsx"
        );

        assertThat(secondResult.canonicalDataSha256())
                .isEqualTo(firstResult.canonicalDataSha256());
        assertThat(secondResult.rules()).hasSize(2);
    }

    private byte[] workbook(
            String documentTitle,
            String sourceSheetName,
            List<Object[]> rules,
            boolean includeBrokenSheet
    ) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (documentTitle != null) {
                workbook.getProperties().getCoreProperties().setTitle(documentTitle);
            }
            Sheet source = workbook.createSheet(sourceSheetName);
            Row header = source.createRow(0);
            for (int index = 0; index < HEADERS.length; index++) {
                header.createCell(index).setCellValue(HEADERS[index]);
            }
            for (int index = 0; index < rules.size(); index++) {
                writeRule(source.createRow(index + 1), index + 1, rules.get(index));
            }
            if (includeBrokenSheet) {
                Sheet broken = workbook.createSheet("Sheet1");
                broken.createRow(0).createCell(0).setCellValue("#REF!");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private Object[] rule(
            Object studentCollege,
            Object studentDepartment,
            Object studentMajor,
            Object offeringCollege,
            Object offeringDepartment,
            Object offeringMajor,
            Object courseCode,
            Object courseName,
            Object effectiveYear,
            Object effectiveSemester
    ) {
        return new Object[]{
                studentCollege, studentDepartment, studentMajor,
                offeringCollege, offeringDepartment, offeringMajor,
                courseCode, courseName, effectiveYear, effectiveSemester
        };
    }

    private void writeRule(Row row, int serial, Object[] values) {
        row.createCell(0).setCellValue(serial);
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Number number) {
                row.createCell(index + 1).setCellValue(number.doubleValue());
            } else {
                row.createCell(index + 1).setCellValue(value.toString());
            }
        }
    }
}
