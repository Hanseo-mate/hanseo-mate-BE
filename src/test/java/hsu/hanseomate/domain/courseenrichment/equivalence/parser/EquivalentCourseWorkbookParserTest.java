package hsu.hanseomate.domain.courseenrichment.equivalence.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseParseResult;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookLoader;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

class EquivalentCourseWorkbookParserTest {

    private final EquivalentCourseWorkbookParser parser = new EquivalentCourseWorkbookParser(
            new CourseWorkbookLoader(),
            10 * 1024 * 1024,
            20,
            500_000
    );

    @Test
    void parsesBlankAndRepeatedSerialAsCurrentGroup() throws Exception {
        byte[] workbook = workbook(
                "2026학년도 2학기 동일교과목 현황",
                List.of(
                        row("1", "0000001", "자료구조"),
                        row("", "0000002", "자료구조응용"),
                        row("1", "0000003", "자료구조특론"),
                        row("2", "0000004", "운영체제"),
                        row("", "0000005", "운영체제응용")
                )
        );

        EquivalentCourseParseResult result = parser.parse(workbook, "다른이름.xlsx");

        assertThat(result.academicYear()).isEqualTo(2026);
        assertThat(result.semester()).isEqualTo(2);
        assertThat(result.requiresReview()).isFalse();
        assertThat(result.groups()).hasSize(2);
        assertThat(result.groups().get(0).members())
                .extracting(member -> member.courseCode())
                .containsExactly("0000001", "0000002", "0000003");
        assertThat(result.groups().get(1).members())
                .extracting(member -> member.courseCode())
                .containsExactly("0000004", "0000005");
    }

    @Test
    void nonContiguousSerialReappearanceRequiresReview() throws Exception {
        EquivalentCourseParseResult result = parser.parse(
                workbook(
                        null,
                        List.of(
                                row("1", "0000001", "첫 과목"),
                                row("2", "0000002", "둘째 과목"),
                                row("1", "0000003", "셋째 과목")
                        )
                ),
                "2026-2 동일교과목현황.xlsx"
        );

        assertThat(result.requiresReview()).isTrue();
        assertThat(result.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("NON_CONTIGUOUS_SERIAL_REAPPEARED");
                    assertThat(issue.severity()).isEqualTo(IssueSeverity.ERROR);
                    assertThat(issue.rowNumber()).isEqualTo(5);
                });
    }

    @Test
    void invalidAndDuplicateCourseCodesRequireReview() throws Exception {
        EquivalentCourseParseResult result = parser.parse(
                workbook(
                        null,
                        List.of(
                                row("1", "123456", "자리수 오류"),
                                row("", "0000001", "중복 과목 1"),
                                row("2", "0000001", "중복 과목 2")
                        )
                ),
                "2026-2 동일교과목현황.xlsx"
        );

        assertThat(result.requiresReview()).isTrue();
        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("INVALID_EQUIVALENT_COURSE_CODE", "DUPLICATE_EQUIVALENT_COURSE_CODE");
    }

    @Test
    void singletonIsWarningAndDoesNotRequireReview() throws Exception {
        EquivalentCourseParseResult result = parser.parse(
                workbook(null, List.<String[]>of(row("1", "0000001", "단독 과목"))),
                "2026-2 동일교과목현황.xlsx"
        );

        assertThat(result.requiresReview()).isFalse();
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("SINGLETON_EQUIVALENT_COURSE_GROUP");
            assertThat(issue.severity()).isEqualTo(IssueSeverity.WARNING);
        });
    }

    @Test
    void canonicalHashIgnoresSerialGroupMemberOrderAndFileName() throws Exception {
        EquivalentCourseParseResult first = parser.parse(
                workbook(
                        null,
                        List.of(
                                row("1", "0000001", "알고리즘"),
                                row("", "0000002", " 알고리즘   응용 "),
                                row("2", "0000003", "네트워크"),
                                row("", "0000004", "네트워크 응용")
                        )
                ),
                "2026-2 첫번째.xlsx"
        );
        EquivalentCourseParseResult reordered = parser.parse(
                workbook(
                        null,
                        List.of(
                                row("99", "0000004", "네트워크 응용"),
                                row("", "0000003", "네트워크"),
                                row("42", "0000002", "알고리즘 응용"),
                                row("", "0000001", "알고리즘")
                        )
                ),
                "2026-2 두번째.xlsx"
        );
        EquivalentCourseParseResult regrouped = parser.parse(
                workbook(
                        null,
                        List.of(
                                row("1", "0000001", "알고리즘"),
                                row("", "0000003", "네트워크"),
                                row("2", "0000002", "알고리즘 응용"),
                                row("", "0000004", "네트워크 응용")
                        )
                ),
                "2026-2 세번째.xlsx"
        );

        assertThat(reordered.canonicalHash()).isEqualTo(first.canonicalHash());
        assertThat(regrouped.canonicalHash()).isNotEqualTo(first.canonicalHash());
    }

    @Test
    void conflictingSemesterSourcesAreRejected() throws Exception {
        byte[] workbook = workbook(
                "2026학년도 1학기 동일교과목 현황",
                List.of(
                        row("1", "0000001", "첫 과목"),
                        row("", "0000002", "둘째 과목")
                )
        );

        assertThatThrownBy(() -> parser.parse(workbook, "2026-2 동일교과목현황.xlsx"))
                .isInstanceOf(CourseWorkbookParseException.class)
                .satisfies(exception -> assertThat(
                        ((CourseWorkbookParseException) exception).code()
                ).isEqualTo("SEMESTER_CONFLICT"));
    }

    @Test
    void acceptsRealEquivalentCourseWorkbookWhenFixturePathIsProvided() throws Exception {
        String fixturePath = System.getenv("EQUIVALENT_WORKBOOK_PATH");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Path path = Path.of(fixturePath);
        Assumptions.assumeTrue(Files.isRegularFile(path));

        EquivalentCourseParseResult result = parser.parse(
                Files.readAllBytes(path),
                path.getFileName().toString()
        );

        assertThat(result.academicYear()).isEqualTo(2026);
        assertThat(result.semester()).isEqualTo(2);
        assertThat(result.requiresReview()).isFalse();
        assertThat(result.groups()).hasSize(2085);
        assertThat(result.memberCount()).isEqualTo(5536);
        assertThat(result.issues())
                .filteredOn(issue -> issue.code().equals("SINGLETON_EQUIVALENT_COURSE_GROUP"))
                .hasSize(9);
        assertThat(result.rawFileSha256())
                .isEqualTo("55b46dea97b46aa63fdd110d363be1a032474197ac9c0ae71b48234d5f3da33d");
    }

    private static String[] row(String serial, String code, String name) {
        return new String[]{serial, code, name};
    }

    private static byte[] workbook(String title, List<String[]> values) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("sheet 1");
            if (title != null) {
                sheet.createRow(0).createCell(0).setCellValue(title);
            }
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
