package hsu.hanseomate.domain.courseimport.parser.common;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.courseimport.dto.ParseIssueRequest;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CourseParserOutputTest {

    @Test
    void capsParserIssuesAndAddsOneBlockingSummary() {
        CourseParserOutput output = new CourseParserOutput("test");

        IntStream.range(0, CourseParserOutput.MAX_RECORDED_ISSUES + 100)
                .mapToObj(index -> new ParseIssueRequest(
                        IssueSeverity.WARNING,
                        "WARNING_" + index,
                        "warning",
                        "sheet",
                        index + 1,
                        "field",
                        null
                ))
                .forEach(output.issues()::add);

        assertThat(output.issues()).hasSize(CourseParserOutput.MAX_RECORDED_ISSUES);
        ParseIssueRequest last = output.issues().get(output.issues().size() - 1);
        assertThat(last.code()).isEqualTo("ISSUES_TRUNCATED");
        assertThat(last.severity()).isEqualTo(IssueSeverity.ERROR);
    }
}
