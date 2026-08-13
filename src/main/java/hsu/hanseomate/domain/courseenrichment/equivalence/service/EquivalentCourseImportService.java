package hsu.hanseomate.domain.courseenrichment.equivalence.service;

import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseGroupData;
import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseImportResponse;
import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseMemberData;
import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseParseResult;
import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseGroup;
import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseImportHistory;
import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseMember;
import hsu.hanseomate.domain.courseenrichment.equivalence.repository.EquivalentCourseImportHistoryRepository;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class EquivalentCourseImportService {

    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final EquivalentCourseImportHistoryRepository importHistoryRepository;

    @Transactional
    public EquivalentCourseImportResponse importSnapshot(EquivalentCourseParseResult result) {
        EquivalentCourseImportHistory reusedImportId = importHistoryRepository
                .findByImportId(result.importId())
                .orElse(null);
        if (reusedImportId != null) {
            if (result.canonicalHash().equals(reusedImportId.getCanonicalHash())) {
                return duplicateResponse(result.importId(), reusedImportId);
            }
            throw new IllegalStateException("이미 사용된 동일교과목 importId입니다.");
        }

        String rawPayloadJson = serialize(result, "동일교과목 원본 JSON을 직렬화할 수 없습니다.");
        String rawIssuesJson = serialize(result.issues(), "동일교과목 이슈 JSON을 직렬화할 수 없습니다.");
        if (result.requiresReview()) {
            persistReviewHistory(result, rawPayloadJson, rawIssuesJson);
            long errorCount = result.issues().stream()
                    .filter(issue -> issue.severity() == IssueSeverity.ERROR)
                    .count();
            return new EquivalentCourseImportResponse(
                    result.importId(),
                    StorageStatus.REVIEW_REQUIRED,
                    false,
                    result.groups().size(),
                    result.memberCount(),
                    "검토가 필요한 오류가 %d개 있어 활성 데이터에 반영하지 않았습니다."
                            .formatted(errorCount),
                    result.issues()
            );
        }

        String activeScopeKey = activeScopeKey(result.academicYear(), result.semester());
        EquivalentCourseImportHistory current = importHistoryRepository
                .findActiveForUpdate(activeScopeKey)
                .orElse(null);
        if (current != null && result.canonicalHash().equals(current.getCanonicalHash())) {
            return duplicateResponse(result.importId(), current);
        }

        if (current != null) {
            current.deactivate();
            entityManager.flush();
        }

        EquivalentCourseImportHistory stored = EquivalentCourseImportHistory.stored(
                result.importId(),
                activeScopeKey,
                result.canonicalHash(),
                result.rawFileSha256(),
                result.fileName(),
                result.schemaVersion(),
                result.parserVersion(),
                result.academicYear(),
                result.semester(),
                result.groups().size(),
                result.memberCount(),
                rawPayloadJson,
                rawIssuesJson
        );
        entityManager.persist(stored);
        persistSnapshot(result.groups(), stored);
        entityManager.flush();

        return new EquivalentCourseImportResponse(
                result.importId(),
                StorageStatus.STORED,
                true,
                result.groups().size(),
                result.memberCount(),
                "%d학년도 %d학기 동일교과목 저장 완료".formatted(
                        result.academicYear(),
                        result.semester()
                ),
                result.issues()
        );
    }

    private void persistReviewHistory(
            EquivalentCourseParseResult result,
            String rawPayloadJson,
            String rawIssuesJson
    ) {
        entityManager.persist(EquivalentCourseImportHistory.reviewRequired(
                result.importId(),
                result.canonicalHash(),
                result.rawFileSha256(),
                result.fileName(),
                result.schemaVersion(),
                result.parserVersion(),
                result.academicYear(),
                result.semester(),
                result.groups().size(),
                result.memberCount(),
                rawPayloadJson,
                rawIssuesJson
        ));
        entityManager.flush();
    }

    private void persistSnapshot(
            List<EquivalentCourseGroupData> groups,
            EquivalentCourseImportHistory history
    ) {
        for (EquivalentCourseGroupData groupData : groups) {
            EquivalentCourseGroup group = EquivalentCourseGroup.create(
                    history,
                    groupData.sourceSerial(),
                    groupData.groupOrder(),
                    groupData.sourceSheet(),
                    groupData.sourceStartRow(),
                    groupData.sourceEndRow()
            );
            entityManager.persist(group);
            for (EquivalentCourseMemberData memberData : groupData.members()) {
                entityManager.persist(EquivalentCourseMember.create(
                        history,
                        group,
                        memberData.courseCode(),
                        memberData.courseName(),
                        memberData.sourceSheet(),
                        memberData.sourceRow(),
                        memberData.memberOrder()
                ));
            }
        }
    }

    private EquivalentCourseImportResponse duplicateResponse(
            String importId,
            EquivalentCourseImportHistory current
    ) {
        return new EquivalentCourseImportResponse(
                importId,
                StorageStatus.DUPLICATE,
                false,
                current.getGroupCount(),
                current.getMemberCount(),
                "이미 반영된 동일교과목 데이터입니다.",
                List.of()
        );
    }

    private String activeScopeKey(int academicYear, int semester) {
        return academicYear + ":" + semester;
    }

    private String serialize(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(errorMessage, exception);
        }
    }
}
