package hsu.hanseomate.domain.courseenrichment.crossmajor.service;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.courseenrichment.crossmajor.entity.CrossMajorRecognitionRule;
import hsu.hanseomate.domain.courseenrichment.crossmajor.repository.CrossMajorRecognitionRuleRepository;
import hsu.hanseomate.domain.courseenrichment.crossmajor.support.CrossMajorRecognitionNormalizer;
import hsu.hanseomate.domain.courseenrichment.crossmajor.type.CrossMajorRecognitionImportStatus;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrossMajorRecognitionQueryService {

    private final CrossMajorRecognitionRuleRepository ruleRepository;

    public List<String> findRecognitions(CourseOffering offering) {
        if (offering == null || offering.getSemester() == null) {
            return List.of();
        }
        String courseNameKey = CrossMajorRecognitionNormalizer.key(offering.getCourseName());
        if (courseNameKey.isBlank()) {
            return List.of();
        }
        int offeringYear = offering.getSemester().getAcademicYear();
        int offeringSemester = offering.getSemester().getSemester();
        List<CrossMajorRecognitionRule> candidates = ruleRepository
                .findActiveCandidatesByCourseName(
                        offeringYear,
                        courseNameKey,
                        CrossMajorRecognitionImportStatus.ACTIVE
                ).stream()
                .filter(rule -> effectiveAtOrBefore(rule, offeringYear, offeringSemester))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<String> departmentNames = new TreeSet<>();
        for (CrossMajorRecognitionRule rule : candidates) {
            departmentNames.add(rule.getStudentDepartmentName());
        }
        return List.copyOf(departmentNames);
    }

    private boolean effectiveAtOrBefore(
            CrossMajorRecognitionRule rule,
            int offeringYear,
            int offeringSemester
    ) {
        return rule.getEffectiveYear() < offeringYear
                || (rule.getEffectiveYear() == offeringYear
                && rule.getEffectiveSemester() <= offeringSemester);
    }
}
