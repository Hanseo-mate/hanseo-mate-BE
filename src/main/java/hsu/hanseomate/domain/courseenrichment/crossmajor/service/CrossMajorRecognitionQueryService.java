package hsu.hanseomate.domain.courseenrichment.crossmajor.service;

import hsu.hanseomate.domain.course.entity.AcademicUnit;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionResponse;
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

    public List<CrossMajorRecognitionResponse> findRecognitions(CourseOffering offering) {
        String courseCode = CrossMajorRecognitionNormalizer.courseCode(offering.getCourseCode());
        if (courseCode == null || offering.getSemester() == null) {
            return List.of();
        }
        int offeringYear = offering.getSemester().getAcademicYear();
        int offeringSemester = offering.getSemester().getSemester();
        List<CrossMajorRecognitionRule> candidates = ruleRepository.findActiveCandidates(
                        offeringYear,
                        courseCode,
                        CrossMajorRecognitionImportStatus.ACTIVE
                ).stream()
                .filter(rule -> effectiveAtOrBefore(rule, offeringYear, offeringSemester))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        String courseNameKey = CrossMajorRecognitionNormalizer.key(offering.getCourseName());
        AcademicUnit academicUnit = offering.getAcademicUnit();
        if (academicUnit == null) {
            return List.of();
        }
        String departmentKey = CrossMajorRecognitionNormalizer.key(
                academicUnit.getDepartmentName()
        );
        String majorKey = CrossMajorRecognitionNormalizer.key(academicUnit.getMajorName());
        List<CrossMajorRecognitionRule> exactOrganization = candidates.stream()
                .filter(rule -> rule.getOfferingDepartmentKey().equals(departmentKey))
                .filter(rule -> rule.getOfferingMajorKey().equals(majorKey))
                .toList();
        List<CrossMajorRecognitionRule> selected = resolveIdentity(
                exactOrganization,
                courseNameKey
        );
        if (selected.isEmpty()) {
            return List.of();
        }

        Set<CrossMajorRecognitionResponse> responses = new TreeSet<>();
        for (CrossMajorRecognitionRule rule : selected) {
            responses.add(new CrossMajorRecognitionResponse(
                    rule.getStudentCollegeName(),
                    rule.getStudentDepartmentName(),
                    rule.getStudentMajorName(),
                    rule.getEffectiveYear(),
                    rule.getEffectiveSemester()
            ));
        }
        return List.copyOf(responses);
    }

    private List<CrossMajorRecognitionRule> resolveIdentity(
            List<CrossMajorRecognitionRule> candidates,
            String courseNameKey
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        long distinctNames = candidates.stream()
                .map(CrossMajorRecognitionRule::getCourseNameKey)
                .distinct()
                .count();
        if (distinctNames == 1) {
            return candidates;
        }
        return candidates.stream()
                .filter(rule -> rule.getCourseNameKey().equals(courseNameKey))
                .toList();
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
