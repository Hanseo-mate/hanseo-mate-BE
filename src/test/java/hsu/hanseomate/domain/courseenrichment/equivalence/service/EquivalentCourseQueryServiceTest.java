package hsu.hanseomate.domain.courseenrichment.equivalence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.Semester;
import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseResponse;
import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseGroup;
import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseMember;
import hsu.hanseomate.domain.courseenrichment.equivalence.repository.EquivalentCourseMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquivalentCourseQueryServiceTest {

    private final EquivalentCourseMemberRepository memberRepository =
            mock(EquivalentCourseMemberRepository.class);
    private final EquivalentCourseQueryService queryService =
            new EquivalentCourseQueryService(memberRepository);

    @Test
    void returnsSameGroupInMemberOrderExcludingCurrentCourse() {
        CourseOffering offering = mock(CourseOffering.class);
        Semester semester = mock(Semester.class);
        EquivalentCourseGroup group = mock(EquivalentCourseGroup.class);
        UUID groupId = UUID.randomUUID();
        EquivalentCourseMember current = member("0000002", "둘째", group);
        EquivalentCourseMember first = member("0000001", "첫째", group);
        EquivalentCourseMember third = member("0000003", "셋째", group);
        when(offering.getCourseCode()).thenReturn("0000002");
        when(offering.getSemester()).thenReturn(semester);
        when(semester.getAcademicYear()).thenReturn(2026);
        when(semester.getSemester()).thenReturn(2);
        when(group.getId()).thenReturn(groupId);
        when(memberRepository.findActiveMember("2026:2", "0000002"))
                .thenReturn(Optional.of(current));
        when(memberRepository.findAllByGroupIdOrderByMemberOrderAsc(groupId))
                .thenReturn(List.of(first, current, third));

        assertThat(queryService.findEquivalentCourses(offering)).containsExactly(
                new EquivalentCourseResponse("0000001", "첫째"),
                new EquivalentCourseResponse("0000003", "셋째")
        );
    }

    @Test
    void returnsEmptyWhenCodeDoesNotBelongToActiveSnapshot() {
        CourseOffering offering = mock(CourseOffering.class);
        Semester semester = mock(Semester.class);
        when(offering.getCourseCode()).thenReturn("0000009");
        when(offering.getSemester()).thenReturn(semester);
        when(semester.getAcademicYear()).thenReturn(2026);
        when(semester.getSemester()).thenReturn(2);
        when(memberRepository.findActiveMember("2026:2", "0000009"))
                .thenReturn(Optional.empty());

        assertThat(queryService.findEquivalentCourses(offering)).isEmpty();
    }

    private EquivalentCourseMember member(
            String code,
            String name,
            EquivalentCourseGroup group
    ) {
        EquivalentCourseMember member = mock(EquivalentCourseMember.class);
        when(member.getCourseCode()).thenReturn(code);
        when(member.getCourseName()).thenReturn(name);
        when(member.getGroup()).thenReturn(group);
        return member;
    }
}
