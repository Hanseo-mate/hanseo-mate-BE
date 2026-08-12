package hsu.hanseomate.domain.timetable.composition.dto;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.support.CourseCyberPolicy;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.search.dto.CourseScheduleResponse;
import hsu.hanseomate.domain.timetable.search.support.GeneralCategoryResolver;
import hsu.hanseomate.domain.timetable.search.type.GeneralCategoryFilter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TimetableCourseResponse(
        Long timetableCourseId,
        UUID courseId,
        String courseCode,
        String courseName,
        String sectionNo,
        BigDecimal credit,
        boolean cyber,
        GeneralCategoryFilter generalCategory,
        List<String> eligibleDepartmentNames,
        String instructorName,
        String scheduleText,
        String classroomText,
        List<CourseScheduleResponse> meetings
) {
    public static TimetableCourseResponse from(
            TimetableCourse timetableCourse,
            List<CourseSchedule> schedules,
            List<String> eligibleDepartmentNames
    ) {
        CourseOffering offering = timetableCourse.getCourseOffering();
        return new TimetableCourseResponse(
                timetableCourse.getId(),
                offering.getId(),
                offering.getCourseCode(),
                offering.getCourseName(),
                offering.getSectionNo(),
                offering.getCredit(),
                CourseCyberPolicy.isCyber(offering),
                GeneralCategoryResolver.resolve(offering.getGeneralEducation()),
                List.copyOf(eligibleDepartmentNames),
                offering.getInstructorName(),
                offering.getScheduleText(),
                offering.getClassroomText(),
                schedules.stream().map(CourseScheduleResponse::from).toList()
        );
    }
}
