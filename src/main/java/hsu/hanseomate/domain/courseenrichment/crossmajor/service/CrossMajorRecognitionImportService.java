package hsu.hanseomate.domain.courseenrichment.crossmajor.service;

import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionImportResponse;
import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionParseResult;
import hsu.hanseomate.domain.courseenrichment.crossmajor.entity.CrossMajorRecognitionImportHistory;
import hsu.hanseomate.domain.courseenrichment.crossmajor.entity.CrossMajorRecognitionRule;
import hsu.hanseomate.domain.courseenrichment.crossmajor.repository.CrossMajorRecognitionImportHistoryRepository;
import hsu.hanseomate.domain.courseenrichment.crossmajor.repository.CrossMajorRecognitionRuleRepository;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CrossMajorRecognitionImportService {

    private static final String SCOPE_PREFIX = "CROSS_MAJOR:";

    private final CrossMajorRecognitionImportHistoryRepository historyRepository;
    private final CrossMajorRecognitionRuleRepository ruleRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Transactional
    public CrossMajorRecognitionImportResponse importParsed(
            CrossMajorRecognitionParseResult parsed
    ) {
        String issuesJson = serialize(parsed.issues());
        String rawPayloadJson = serialize(parsed.rules());
        if (parsed.hasErrors()) {
            CrossMajorRecognitionImportHistory review = historyRepository.save(
                    CrossMajorRecognitionImportHistory.reviewRequired(
                            parsed.policyYear(),
                            parsed.uploadedSemester(),
                            parsed.fileName(),
                            parsed.rawFileSha256(),
                            parsed.canonicalDataSha256(),
                            parsed.sourceSheet(),
                            parsed.rawRowCount(),
                            parsed.rules().size(),
                            parsed.warningCount(),
                            issuesJson,
                            rawPayloadJson
                    )
            );
            return response(
                    review,
                    StorageStatus.REVIEW_REQUIRED,
                    false,
                    0,
                    "검토가 필요한 행이 있어 기존 활성 데이터를 변경하지 않았습니다.",
                    parsed.issues(),
                    parsed.uploadedSemester()
            );
        }

        String activeScopeKey = SCOPE_PREFIX + parsed.policyYear();
        CrossMajorRecognitionImportHistory current = historyRepository
                .findActiveForUpdate(activeScopeKey)
                .orElse(null);
        if (current != null
                && current.getCanonicalDataSha256().equals(parsed.canonicalDataSha256())) {
            return response(
                    current,
                    StorageStatus.DUPLICATE,
                    false,
                    current.getRuleCount(),
                    "같은 정책 연도의 동일한 전공인정 데이터가 이미 반영되어 있습니다.",
                    List.of(),
                    parsed.uploadedSemester()
            );
        }

        if (current != null) {
            current.markSuperseded();
            historyRepository.saveAndFlush(current);
        }

        CrossMajorRecognitionImportHistory active = historyRepository.saveAndFlush(
                CrossMajorRecognitionImportHistory.active(
                        parsed.policyYear(),
                        parsed.uploadedSemester(),
                        activeScopeKey,
                        parsed.fileName(),
                        parsed.rawFileSha256(),
                        parsed.canonicalDataSha256(),
                        parsed.sourceSheet(),
                        parsed.rawRowCount(),
                        parsed.rules().size(),
                        parsed.warningCount(),
                        issuesJson,
                        rawPayloadJson
                )
        );
        ruleRepository.saveAll(parsed.rules().stream()
                .map(rule -> CrossMajorRecognitionRule.create(active, rule))
                .toList());
        entityManager.flush();

        return response(
                active,
                StorageStatus.STORED,
                true,
                parsed.rules().size(),
                "%d학년도 타학과 전공인정 규칙 저장 완료".formatted(parsed.policyYear()),
                parsed.issues(),
                parsed.uploadedSemester()
        );
    }

    private CrossMajorRecognitionImportResponse response(
            CrossMajorRecognitionImportHistory history,
            StorageStatus storageStatus,
            boolean databaseChanged,
            int ruleCount,
            String message,
            List<hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionIssueResponse>
                    issues,
            int responseUploadedSemester
    ) {
        return new CrossMajorRecognitionImportResponse(
                history.getId(),
                storageStatus,
                databaseChanged,
                history.getPolicyYear(),
                responseUploadedSemester,
                ruleCount,
                message,
                issues
        );
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("전공인정 업로드 원본을 직렬화할 수 없습니다.", exception);
        }
    }
}
