package hsu.hanseomate.domain.courseenrichment.equivalence.service;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.Semester;
import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseResponse;
import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseMember;
import hsu.hanseomate.domain.courseenrichment.equivalence.repository.EquivalentCourseMemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EquivalentCourseQueryService {

    private final EquivalentCourseMemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<EquivalentCourseResponse> findEquivalentCourses(CourseOffering offering) {
        if (offering == null || offering.getCourseCode() == null
                || offering.getCourseCode().isBlank() || offering.getSemester() == null) {
            return List.of();
        }

        Semester semester = offering.getSemester();
        String activeScopeKey = semester.getAcademicYear() + ":" + semester.getSemester();
        EquivalentCourseMember current = memberRepository
                .findActiveMember(activeScopeKey, offering.getCourseCode())
                .orElse(null);
        if (current == null) {
            return List.of();
        }

        return memberRepository.findAllByGroupIdOrderByMemberOrderAsc(current.getGroup().getId())
                .stream()
                .filter(member -> !member.getCourseCode().equals(current.getCourseCode()))
                .map(member -> new EquivalentCourseResponse(
                        member.getCourseCode(),
                        member.getCourseName()
                ))
                .toList();
    }
}
