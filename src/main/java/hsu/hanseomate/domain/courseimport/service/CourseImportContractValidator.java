package hsu.hanseomate.domain.courseimport.service;

import hsu.hanseomate.domain.courseimport.dto.CourseScheduleRequest;
import hsu.hanseomate.domain.courseimport.dto.GeneralCategoryNodeRequest;
import hsu.hanseomate.domain.courseimport.dto.LectureRequest;
import hsu.hanseomate.domain.courseimport.dto.SourceCellRequest;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import hsu.hanseomate.domain.courseimport.dto.type.ParseStatus;
import hsu.hanseomate.domain.courseimport.exception.CourseImportContractException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CourseImportContractValidator {

    public static final String SCHEMA_VERSION = "1.2";
    private static final int MIN_PERIOD = 1;
    private static final int MAX_PERIOD = 30;
    private static final Set<String> BLOCKING_ISSUE_CODES = Set.of(
            "AMBIGUOUS_GENERAL_HEADING_LEVEL",
            "AMBIGUOUS_HEADING_HIERARCHY",
            "UNSUPPORTED_MAJOR_HEADER",
            "UNSUPPORTED_GENERAL_HEADER"
    );

    public void validateTransport(
            TimetableParseResultRequest request,
            CurriculumType routeType,
            String headerImportId,
            String headerSchemaVersion,
            String idempotencyKey
    ) {
        if (!SCHEMA_VERSION.equals(request.schemaVersion())
                || !SCHEMA_VERSION.equals(headerSchemaVersion)) {
            throw new CourseImportContractException("지원하지 않는 parser schema version입니다.");
        }
        if (!request.importId().equals(headerImportId)) {
            throw new CourseImportContractException("X-IMPORT-ID와 body의 importId가 일치하지 않습니다.");
        }
        if (request.curriculumType() != routeType) {
            throw new CourseImportContractException("호출 경로와 curriculumType이 일치하지 않습니다.");
        }
        String expectedIdempotencyKey = expectedIdempotencyKey(request);
        if (!expectedIdempotencyKey.equals(idempotencyKey)) {
            throw new CourseImportContractException("Idempotency-Key가 요청 내용과 일치하지 않습니다.");
        }
    }

    public boolean requiresReview(TimetableParseResultRequest request) {
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
        if (!request.fileSha256().matches("[0-9a-f]{64}")) {
            return true;
        }
        if (!validTaxonomy(request.generalCategoryNodes())) {
            return true;
        }
        return request.lectures().stream().anyMatch(lecture -> !validLecture(request, lecture));
    }

    public String expectedIdempotencyKey(TimetableParseResultRequest request) {
        return "%d:%d:%s:%s".formatted(
                request.academicYear(),
                request.semester(),
                request.curriculumType(),
                request.fileSha256()
        );
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
        states.put(nodeKey, VisitState.VISITING);
        String parentKey = nodeByKey.get(nodeKey).parentKey();
        if (parentKey != null && hasCycle(parentKey, nodeByKey, states)) {
            return true;
        }
        states.put(nodeKey, VisitState.VISITED);
        return false;
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
