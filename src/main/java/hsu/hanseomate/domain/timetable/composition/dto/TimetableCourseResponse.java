package hsu.hanseomate.domain.timetable.composition.dto;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.search.dto.CourseScheduleResponse;
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
        String instructorName,
        String scheduleText,
        String classroomText,
        List<CourseScheduleResponse> meetings
) {
    public static TimetableCourseResponse from(
            TimetableCourse timetableCourse,
            List<CourseSchedule> schedules
    ) {
        CourseOffering offering = timetableCourse.getCourseOffering();
        return new TimetableCourseResponse(
                timetableCourse.getId(),
                offering.getId(),
                offering.getCourseCode(),
                offering.getCourseName(),
                offering.getSectionNo(),
                offering.getCredit(),
                offering.getInstructorName(),
                offering.getScheduleText(),
                offering.getClassroomText(),
                schedules.stream().map(CourseScheduleResponse::from).toList()
        );
    }
}
