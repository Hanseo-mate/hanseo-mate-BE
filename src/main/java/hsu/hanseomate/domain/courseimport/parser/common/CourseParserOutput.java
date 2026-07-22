package hsu.hanseomate.domain.courseimport.parser.common;

import hsu.hanseomate.domain.courseimport.dto.AcademicUnitRequest;
import hsu.hanseomate.domain.courseimport.dto.GeneralCategoryNodeRequest;
import hsu.hanseomate.domain.courseimport.dto.GeneralEducationContextRequest;
import hsu.hanseomate.domain.courseimport.dto.LectureRequest;
import hsu.hanseomate.domain.courseimport.dto.ParseIssueRequest;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Mutable accumulator used while walking workbook rows. */
public final class CourseParserOutput {

    static final int MAX_RECORDED_ISSUES = 1000;

    private final String parserVersion;
    private final List<LectureRequest> lectures = new ArrayList<>();
    private final List<ParseIssueRequest> issues = new BoundedIssueList();
    private final List<AcademicUnitRequest> academicUnits = new ArrayList<>();
    private final List<GeneralEducationContextRequest> generalCategories = new ArrayList<>();
    private final List<GeneralCategoryNodeRequest> generalCategoryNodes = new ArrayList<>();
    private int headerCount;
    private int failedRowCount;

    public CourseParserOutput(String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public String parserVersion() {
        return parserVersion;
    }

    public List<LectureRequest> lectures() {
        return lectures;
    }

    public List<ParseIssueRequest> issues() {
        return issues;
    }

    public List<AcademicUnitRequest> academicUnits() {
        return academicUnits;
    }

    public List<GeneralEducationContextRequest> generalCategories() {
        return generalCategories;
    }

    public List<GeneralCategoryNodeRequest> generalCategoryNodes() {
        return generalCategoryNodes;
    }

    public int headerCount() {
        return headerCount;
    }

    public void incrementHeaderCount() {
        headerCount++;
    }

    public int failedRowCount() {
        return failedRowCount;
    }

    public void incrementFailedRowCount() {
        failedRowCount++;
    }

    private static final class BoundedIssueList extends ArrayList<ParseIssueRequest> {

        private boolean truncated;

        @Override
        public boolean add(ParseIssueRequest issue) {
            if (truncated) {
                return false;
            }
            if (size() < MAX_RECORDED_ISSUES - 1) {
                return super.add(issue);
            }
            truncated = true;
            return super.add(new ParseIssueRequest(
                    IssueSeverity.ERROR,
                    "ISSUES_TRUNCATED",
                    "검토 항목이 너무 많아 앞 999건까지만 기록했습니다.",
                    null,
                    null,
                    null,
                    null
            ));
        }

        @Override
        public boolean addAll(Collection<? extends ParseIssueRequest> issues) {
            boolean changed = false;
            for (ParseIssueRequest issue : issues) {
                changed |= add(issue);
            }
            return changed;
        }
    }
}
