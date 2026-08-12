package hsu.hanseomate.domain.timetable.search.dto;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.timetable.search.type.GeneralCategoryFilter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CourseOfferingDetailResponse(
        UUID offeringId,
        String courseName,
        String sectionNo,
        BigDecimal credit,
        boolean cyber,
        String instructorName,
        CurriculumType curriculumType,
        Integer targetGrade,
        String originalAcademicUnitName,
        List<String> eligibleDepartmentNames,
        GeneralCategoryFilter generalCategory,
        List<CourseSearchScheduleResponse> schedules,
        String note
) {
    public static CourseOfferingDetailResponse from(
            CourseOffering offering,
            List<CourseSchedule> schedules,
            List<String> eligibleDepartmentNames
    ) {
        CourseOfferingResponse summary = CourseOfferingResponse.from(
                offering,
                schedules,
                eligibleDepartmentNames
        );
        return new CourseOfferingDetailResponse(
                summary.offeringId(),
                summary.courseName(),
                summary.sectionNo(),
                summary.credit(),
                summary.cyber(),
                summary.instructorName(),
                summary.curriculumType(),
                summary.targetGrade(),
                summary.originalAcademicUnitName(),
                summary.eligibleDepartmentNames(),
                summary.generalCategory(),
                summary.schedules(),
                offering.getNote()
        );
    }
}
