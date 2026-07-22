package hsu.hanseomate.domain.courseimport.service;

import hsu.hanseomate.domain.courseimport.dto.CourseScheduleRequest;
import hsu.hanseomate.domain.courseimport.dto.CourseImportIssueResponse;
import hsu.hanseomate.domain.courseimport.dto.GeneralCategoryNodeRequest;
import hsu.hanseomate.domain.courseimport.dto.LectureRequest;
import hsu.hanseomate.domain.courseimport.dto.SourceCellRequest;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import hsu.hanseomate.domain.courseimport.dto.type.ParseStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CourseImportContractValidator {

    private static final int MIN_PERIOD = 1;
    private static final int MAX_PERIOD = 30;
    static final int MAX_REVIEW_ISSUES = 1000;
    private static final Set<String> BLOCKING_ISSUE_CODES = Set.of(
            "AMBIGUOUS_GENERAL_HEADING_LEVEL",
            "AMBIGUOUS_HEADING_HIERARCHY",
            "UNSUPPORTED_MAJOR_HEADER",
            "UNSUPPORTED_GENERAL_HEADER"
    );
    private static final Pattern INDEXED_PATH = Pattern.compile(
            "^(lectures|generalCategoryNodes|issues)\\[(\\d+)](?:\\.(.+))?$"
    );
    private static final int MAX_REVIEW_RAW_VALUE_LENGTH = 2000;

    private final Validator beanValidator;

    public CourseImportContractValidator(Validator beanValidator) {
        this.beanValidator = beanValidator;
    }

    public boolean requiresReview(TimetableParseResultRequest request) {
        if (!beanValidator.validate(request).isEmpty()) {
            return true;
        }
        if (request.issues().size() > MAX_REVIEW_ISSUES) {
            return true;
        }
        if (request.status() != ParseStatus.READY || request.statistics().errorCount() > 0) {
            return true;
        }
        if (request.issues().stream().anyMatch(issue ->
                issue.severity() == IssueSeverity.ERROR || BLOCKING_ISSUE_CODES.contains(issue.code()))) {
            return true;
        }
        if (request.statistics().parsedLectureCount() != request.lectures().size()) {
            return true;
        }
        if (!validFileSha256(request.fileSha256())) {
            return true;
        }
        if (!validTaxonomy(request.generalCategoryNodes())) {
            return true;
        }
        return request.lectures().stream().anyMatch(lecture -> !validLecture(request, lecture));
    }

    public List<CourseImportIssueResponse> reviewIssues(TimetableParseResultRequest request) {
        List<CourseImportIssueResponse> reviewIssues = new BoundedReviewIssueList();
        collectConstraintReviewIssues(request, reviewIssues);
        request.issues().stream()
                .filter(issue -> issue.severity() == IssueSeverity.ERROR
                        || BLOCKING_ISSUE_CODES.contains(issue.code()))
                .map(CourseImportIssueResponse::from)
                .forEach(reviewIssues::add);

        if (request.issues().size() > MAX_REVIEW_ISSUES) {
            reviewIssues.add(issuesTruncated());
        }

        if (request.status() != ParseStatus.READY && reviewIssues.isEmpty()) {
            reviewIssues.add(CourseImportIssueResponse.error(
                    "PARSER_REVIEW_REQUIRED",
                    "파서가 검토 필요 상태로 판정했습니다.",
                    null,
                    null,
                    "status",
                    request.status().name()
            ));
        }
        boolean hasParserError = request.issues().stream()
                .anyMatch(issue -> issue.severity() == IssueSeverity.ERROR);
        if (request.statistics().errorCount() > 0 && !hasParserError) {
            reviewIssues.add(CourseImportIssueResponse.error(
                    "PARSER_ERROR_COUNT_WITHOUT_DETAIL",
                    "파서 통계에 오류가 기록됐지만 상세 오류 정보가 없습니다.",
                    null,
                    null,
                    "statistics.errorCount",
                    String.valueOf(request.statistics().errorCount())
            ));
        }
        if (request.statistics().parsedLectureCount() != request.lectures().size()) {
            reviewIssues.add(CourseImportIssueResponse.error(
                    "PARSED_LECTURE_COUNT_MISMATCH",
                    "파서 통계의 강좌 수와 실제 전달된 강좌 수가 다릅니다.",
                    null,
                    null,
                    "statistics.parsedLectureCount",
                    request.statistics().parsedLectureCount() + "/" + request.lectures().size()
            ));
        }
        if (!validFileSha256(request.fileSha256())) {
            reviewIssues.add(CourseImportIssueResponse.error(
                    "INVALID_FILE_SHA256",
                    "파일 해시 형식이 올바르지 않습니다.",
                    null,
                    null,
                    "fileSha256",
                    request.fileSha256()
            ));
        }

        collectTaxonomyReviewIssues(request.generalCategoryNodes(), reviewIssues);
        request.lectures().forEach(lecture ->
                collectLectureReviewIssues(request, lecture, reviewIssues));

        if (reviewIssues.isEmpty() && requiresReview(request)) {
            reviewIssues.add(CourseImportIssueResponse.error(
                    "SPRING_STORAGE_VALIDATION_FAILED",
                    "강좌 데이터가 Spring 저장 조건을 통과하지 못했습니다.",
                    null,
                    null,
                    null,
                    null
            ));
        }
        return List.copyOf(reviewIssues);
    }

    private void collectConstraintReviewIssues(
            TimetableParseResultRequest request,
            List<CourseImportIssueResponse> reviewIssues
    ) {
        beanValidator.validate(request).stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> constraintIssue(request, violation))
                .forEach(reviewIssues::add);
    }

    private CourseImportIssueResponse constraintIssue(
            TimetableParseResultRequest request,
            ConstraintViolation<TimetableParseResultRequest> violation
    ) {
        String propertyPath = violation.getPropertyPath().toString();
        IssueLocation location = issueLocation(request, propertyPath);
        return CourseImportIssueResponse.error(
                "FIELD_CONSTRAINT_VIOLATION",
                "입력값이 저장 가능한 형식 또는 길이를 벗어났습니다.",
                location.sheetName(),
                location.rowNumber(),
                propertyPath,
                truncateRawValue(violation.getInvalidValue())
        );
    }

    private IssueLocation issueLocation(
            TimetableParseResultRequest request,
            String propertyPath
    ) {
        Matcher matcher = INDEXED_PATH.matcher(propertyPath);
        if (!matcher.matches()) {
            return IssueLocation.NONE;
        }
        int index = Integer.parseInt(matcher.group(2));
        return switch (matcher.group(1)) {
            case "lectures" -> index < request.lectures().size()
                    ? new IssueLocation(
                            request.lectures().get(index).sourceSheet(),
                            request.lectures().get(index).sourceRow()
                    )
                    : IssueLocation.NONE;
            case "generalCategoryNodes" -> index < request.generalCategoryNodes().size()
                    ? new IssueLocation(
                            request.generalCategoryNodes().get(index).sourceSheet(),
                            request.generalCategoryNodes().get(index).sourceRow()
                    )
                    : IssueLocation.NONE;
            case "issues" -> index < request.issues().size()
                    ? new IssueLocation(
                            request.issues().get(index).sheetName(),
                            request.issues().get(index).rowNumber()
                    )
                    : IssueLocation.NONE;
            default -> IssueLocation.NONE;
        };
    }

    private String truncateRawValue(Object invalidValue) {
        if (invalidValue == null) {
            return null;
        }
        String value = String.valueOf(invalidValue);
        if (value.length() <= MAX_REVIEW_RAW_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_REVIEW_RAW_VALUE_LENGTH);
    }

    private boolean validFileSha256(String fileSha256) {
        return fileSha256 != null && fileSha256.matches("[0-9a-f]{64}");
    }

    private void collectTaxonomyReviewIssues(
            List<GeneralCategoryNodeRequest> nodes,
            List<CourseImportIssueResponse> reviewIssues
    ) {
        Map<String, GeneralCategoryNodeRequest> nodeByKey = new HashMap<>();
        for (GeneralCategoryNodeRequest node : nodes) {
            if (nodeByKey.putIfAbsent(node.nodeKey(), node) != null) {
                reviewIssues.add(CourseImportIssueResponse.error(
                        "DUPLICATE_GENERAL_CATEGORY_NODE_KEY",
                        "교양 분류 키가 중복되었습니다.",
                        node.sourceSheet(),
                        node.sourceRow(),
                        "generalCategoryNodes.nodeKey",
                        node.nodeKey()
                ));
            }
        }
        for (GeneralCategoryNodeRequest node : nodes) {
            if (node.parentKey() != null && !nodeByKey.containsKey(node.parentKey())) {
                reviewIssues.add(CourseImportIssueResponse.error(
                        "DANGLING_GENERAL_CATEGORY_PARENT",
                        "교양 분류의 상위 항목을 찾을 수 없습니다.",
                        node.sourceSheet(),
                        node.sourceRow(),
                        "generalCategoryNodes.parentKey",
                        node.parentKey()
                ));
            }
        }

        Map<String, VisitState> states = new HashMap<>();
        for (GeneralCategoryNodeRequest node : nodes) {
            if (hasCycle(node.nodeKey(), nodeByKey, states)) {
                reviewIssues.add(CourseImportIssueResponse.error(
                        "CYCLIC_GENERAL_CATEGORY_TREE",
                        "교양 분류의 상하위 관계가 순환합니다.",
                        node.sourceSheet(),
                        node.sourceRow(),
                        "generalCategoryNodes.parentKey",
                        node.parentKey()
                ));
                break;
            }
        }
    }

    private void collectLectureReviewIssues(
            TimetableParseResultRequest request,
            LectureRequest lecture,
            List<CourseImportIssueResponse> reviewIssues
    ) {
        if (lecture.curriculumType() != request.curriculumType()) {
            reviewIssues.add(CourseImportIssueResponse.error(
                    "CURRICULUM_TYPE_MISMATCH",
                    "강좌의 교육과정 유형이 업로드 유형과 다릅니다.",
                    lecture.sourceSheet(),
                    lecture.sourceRow(),
                    "curriculumType",
                    lecture.curriculumType().name()
            ));
        }
        if (request.curriculumType() == CurriculumType.MAJOR && lecture.academicUnit() == null) {
            reviewIssues.add(CourseImportIssueResponse.error(
                    "MISSING_ACADEMIC_UNIT",
                    "전공 강좌의 학과 또는 전공 정보가 없습니다.",
                    lecture.sourceSheet(),
                    lecture.sourceRow(),
                    "academicUnit",
                    null
            ));
        }
        if (request.curriculumType() == CurriculumType.GENERAL_EDUCATION
                && lecture.generalEducation() == null) {
            reviewIssues.add(CourseImportIssueResponse.error(
                    "MISSING_GENERAL_EDUCATION_CONTEXT",
                    "교양 강좌의 교양 분류 정보가 없습니다.",
                    lecture.sourceSheet(),
                    lecture.sourceRow(),
                    "generalEducation",
                    null
            ));
        }
        lecture.allowedGrades().stream()
                .filter(grade -> grade < 1 || grade > 6)
                .forEach(grade -> reviewIssues.add(CourseImportIssueResponse.error(
                        "INVALID_ALLOWED_GRADE",
                        "수강 가능 학년은 1~6 범위여야 합니다.",
                        lecture.sourceSheet(),
                        lecture.sourceRow(),
                        "allowedGrades",
                        String.valueOf(grade)
                )));

        collectDecimalReviewIssue(
                lecture,
                lecture.credit(),
                "credit",
                "INVALID_CREDIT",
                reviewIssues
        );
        collectDecimalReviewIssue(
                lecture,
                lecture.classHours(),
                "classHours",
                "INVALID_CLASS_HOURS",
                reviewIssues
        );

        collectSourceCellReviewIssues(lecture, reviewIssues);
        collectScheduleReviewIssues(lecture, reviewIssues);
    }

    private void collectDecimalReviewIssue(
            LectureRequest lecture,
            Double value,
            String field,
            String code,
            List<CourseImportIssueResponse> reviewIssues
    ) {
        if (decimalCompatible(value)) {
            return;
        }
        reviewIssues.add(CourseImportIssueResponse.error(
                code,
                "0 이상이며 정수부 5자리·소수부 3자리 이내의 숫자여야 합니다.",
                lecture.sourceSheet(),
                lecture.sourceRow(),
                field,
                String.valueOf(value)
        ));
    }

    private void collectSourceCellReviewIssues(
            LectureRequest lecture,
            List<CourseImportIssueResponse> reviewIssues
    ) {
        Set<Integer> indexes = new HashSet<>();
        Map<String, String> canonicalValues = new HashMap<>();
        for (SourceCellRequest cell : lecture.sourceCells()) {
            if (!indexes.add(cell.columnIndex())) {
                reviewIssues.add(CourseImportIssueResponse.error(
                        "DUPLICATE_SOURCE_COLUMN_INDEX",
                        "같은 원본 열 번호가 중복되었습니다.",
                        lecture.sourceSheet(),
                        lecture.sourceRow(),
                        "sourceCells.columnIndex",
                        String.valueOf(cell.columnIndex())
                ));
            }
            if (cell.canonicalField() != null && cell.value() != null) {
                canonicalValues.putIfAbsent(cell.canonicalField(), cell.value());
            }
        }
        if (lecture.courseCode() != null
                && canonicalValues.containsKey("courseCode")
                && !lecture.courseCode().equals(canonicalValues.get("courseCode"))) {
            reviewIssues.add(CourseImportIssueResponse.error(
                    "COURSE_CODE_SOURCE_MISMATCH",
                    "정규화된 학수번호와 원본 셀 값이 다릅니다.",
                    lecture.sourceSheet(),
                    lecture.sourceRow(),
                    "courseCode",
                    canonicalValues.get("courseCode")
            ));
        }
        if (lecture.sectionNo() != null
                && canonicalValues.containsKey("sectionNo")
                && !lecture.sectionNo().equals(canonicalValues.get("sectionNo"))) {
            reviewIssues.add(CourseImportIssueResponse.error(
                    "SECTION_NO_SOURCE_MISMATCH",
                    "정규화된 분반과 원본 셀 값이 다릅니다.",
                    lecture.sourceSheet(),
                    lecture.sourceRow(),
                    "sectionNo",
                    canonicalValues.get("sectionNo")
            ));
        }
    }

    private void collectScheduleReviewIssues(
            LectureRequest lecture,
            List<CourseImportIssueResponse> reviewIssues
    ) {
        Set<String> scheduleKeys = new HashSet<>();
        for (CourseScheduleRequest schedule : lecture.schedules()) {
            Set<Integer> periods = new HashSet<>();
            for (Integer period : schedule.periods()) {
                if (period < MIN_PERIOD || period > MAX_PERIOD) {
                    reviewIssues.add(CourseImportIssueResponse.error(
                            "INVALID_PERIOD",
                            "교시는 1~30 범위여야 합니다.",
                            lecture.sourceSheet(),
                            lecture.sourceRow(),
                            "scheduleText",
                            String.valueOf(period)
                    ));
                } else if (!periods.add(period)) {
                    reviewIssues.add(CourseImportIssueResponse.error(
                            "DUPLICATE_PERIOD",
                            "같은 수업 일정에 동일한 교시가 중복되었습니다.",
                            lecture.sourceSheet(),
                            lecture.sourceRow(),
                            "scheduleText",
                            String.valueOf(period)
                    ));
                }
            }
            String classroomKey = schedule.classroom() == null
                    ? ""
                    : String.join("|",
                            nullable(schedule.classroom().campusCode()),
                            nullable(schedule.classroom().buildingName()),
                            nullable(schedule.classroom().roomNumber()),
                            schedule.classroom().originalValue());
            String scheduleKey = schedule.dayOfWeek() + "|" + schedule.periods() + "|" + classroomKey;
            if (!scheduleKeys.add(scheduleKey)) {
                reviewIssues.add(CourseImportIssueResponse.error(
                        "DUPLICATE_SCHEDULE",
                        "동일한 수업 일정이 중복되었습니다.",
                        lecture.sourceSheet(),
                        lecture.sourceRow(),
                        "schedules",
                        scheduleKey
                ));
            }
        }
    }

    private boolean validLecture(TimetableParseResultRequest request, LectureRequest lecture) {
        if (lecture.curriculumType() != request.curriculumType()) {
            return false;
        }
        if (request.curriculumType() == CurriculumType.MAJOR && lecture.academicUnit() == null) {
            return false;
        }
        if (request.curriculumType() == CurriculumType.GENERAL_EDUCATION
                && lecture.generalEducation() == null) {
            return false;
        }
        if (lecture.allowedGrades().stream().anyMatch(grade -> grade < 1 || grade > 6)) {
            return false;
        }
        if (!decimalCompatible(lecture.credit()) || !decimalCompatible(lecture.classHours())) {
            return false;
        }
        if (!validSourceCells(lecture)) {
            return false;
        }

        Set<String> scheduleKeys = new HashSet<>();
        for (CourseScheduleRequest schedule : lecture.schedules()) {
            Set<Integer> periods = new HashSet<>();
            for (Integer period : schedule.periods()) {
                if (period < MIN_PERIOD || period > MAX_PERIOD || !periods.add(period)) {
                    return false;
                }
            }
            String classroomKey = schedule.classroom() == null
                    ? ""
                    : String.join("|",
                            nullable(schedule.classroom().campusCode()),
                            nullable(schedule.classroom().buildingName()),
                            nullable(schedule.classroom().roomNumber()),
                            schedule.classroom().originalValue());
            String scheduleKey = schedule.dayOfWeek() + "|" + schedule.periods() + "|" + classroomKey;
            if (!scheduleKeys.add(scheduleKey)) {
                return false;
            }
        }
        return true;
    }

    private boolean validSourceCells(LectureRequest lecture) {
        Set<Integer> indexes = new HashSet<>();
        Map<String, String> canonicalValues = new HashMap<>();
        for (SourceCellRequest cell : lecture.sourceCells()) {
            if (!indexes.add(cell.columnIndex())) {
                return false;
            }
            if (cell.canonicalField() != null && cell.value() != null) {
                canonicalValues.putIfAbsent(cell.canonicalField(), cell.value());
            }
        }
        if (lecture.courseCode() != null
                && canonicalValues.containsKey("courseCode")
                && !lecture.courseCode().equals(canonicalValues.get("courseCode"))) {
            return false;
        }
        return lecture.sectionNo() == null
                || !canonicalValues.containsKey("sectionNo")
                || lecture.sectionNo().equals(canonicalValues.get("sectionNo"));
    }

    private boolean validTaxonomy(List<GeneralCategoryNodeRequest> nodes) {
        Map<String, GeneralCategoryNodeRequest> nodeByKey = new HashMap<>();
        for (GeneralCategoryNodeRequest node : nodes) {
            if (nodeByKey.put(node.nodeKey(), node) != null) {
                return false;
            }
        }
        for (GeneralCategoryNodeRequest node : nodes) {
            if (node.parentKey() != null && !nodeByKey.containsKey(node.parentKey())) {
                return false;
            }
        }
        Map<String, VisitState> states = new HashMap<>();
        for (String nodeKey : nodeByKey.keySet()) {
            if (hasCycle(nodeKey, nodeByKey, states)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasCycle(
            String nodeKey,
            Map<String, GeneralCategoryNodeRequest> nodeByKey,
            Map<String, VisitState> states
    ) {
        VisitState state = states.get(nodeKey);
        if (state == VisitState.VISITING) {
            return true;
        }
        if (state == VisitState.VISITED) {
            return false;
        }
        GeneralCategoryNodeRequest node = nodeByKey.get(nodeKey);
        if (node == null) {
            states.put(nodeKey, VisitState.VISITED);
            return false;
        }
        states.put(nodeKey, VisitState.VISITING);
        String parentKey = node.parentKey();
        if (parentKey != null && hasCycle(parentKey, nodeByKey, states)) {
            return true;
        }
        states.put(nodeKey, VisitState.VISITED);
        return false;
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private boolean decimalCompatible(Double value) {
        if (value == null) {
            return true;
        }
        if (!Double.isFinite(value) || value < 0) {
            return false;
        }
        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        int fractionDigits = Math.max(decimal.scale(), 0);
        int integerDigits = Math.max(decimal.precision() - decimal.scale(), 0);
        return integerDigits <= 5 && fractionDigits <= 3;
    }

    private static CourseImportIssueResponse issuesTruncated() {
        return CourseImportIssueResponse.error(
                "ISSUES_TRUNCATED",
                "검토 항목이 너무 많아 앞 999건까지만 반환하고 저장합니다.",
                null,
                null,
                null,
                null
        );
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    private record IssueLocation(String sheetName, Integer rowNumber) {
        private static final IssueLocation NONE = new IssueLocation(null, null);
    }

    private static final class BoundedReviewIssueList
            extends ArrayList<CourseImportIssueResponse> {

        private boolean truncated;

        @Override
        public boolean add(CourseImportIssueResponse issue) {
            if (truncated) {
                return false;
            }
            if (size() < MAX_REVIEW_ISSUES - 1) {
                return super.add(issue);
            }
            truncated = true;
            return super.add(issuesTruncated());
        }

        @Override
        public boolean addAll(Collection<? extends CourseImportIssueResponse> issues) {
            boolean changed = false;
            for (CourseImportIssueResponse issue : issues) {
                changed |= add(issue);
            }
            return changed;
        }
    }
}
