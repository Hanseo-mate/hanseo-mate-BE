package hsu.hanseomate.domain.courseimport.parser.common;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import org.junit.jupiter.api.Test;

class ScheduleParserTest {

    @Test
    void mismatchedScheduleAndClassroomCountsRequireReview() {
        ScheduleParser.ScheduleParseResult result = ScheduleParser.parse(
                "월1,2 / 화3,4 / 수5,6",
                "본관101, 본관102",
                "전공",
                12
        );

        assertThat(result.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("SCHEDULE_CLASSROOM_COUNT_MISMATCH");
                    assertThat(issue.severity()).isEqualTo(IssueSeverity.ERROR);
                    assertThat(issue.sheetName()).isEqualTo("전공");
                    assertThat(issue.rowNumber()).isEqualTo(12);
                    assertThat(issue.field()).isEqualTo("classroomText");
                });
    }

    @Test
    void excessivelyLargePeriodReturnsLocatedReviewInsteadOfThrowing() {
        String raw = "월999999999999999999999999";

        ScheduleParser.ScheduleParseResult result = ScheduleParser.parse(
                raw,
                "본관101",
                "2026학년도 1학기",
                27
        );

        assertThat(result.issues())
                .anySatisfy(issue -> {
                    assertThat(issue.code()).isEqualTo("INVALID_PERIOD");
                    assertThat(issue.severity()).isEqualTo(IssueSeverity.ERROR);
                    assertThat(issue.sheetName()).isEqualTo("2026학년도 1학기");
                    assertThat(issue.rowNumber()).isEqualTo(27);
                    assertThat(issue.field()).isEqualTo("scheduleText");
                    assertThat(issue.rawValue()).isEqualTo(raw);
                });
    }
}
