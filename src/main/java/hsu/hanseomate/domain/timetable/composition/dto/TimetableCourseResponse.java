package hsu.hanseomate.domain.timetable.composition.dto;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.support.CourseCyberPolicy;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.search.support.GeneralCategoryResolver;
import hsu.hanseomate.domain.timetable.search.type.GeneralCategoryFilter;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public record TimetableCourseResponse(
        Long timetableCourseId,
        boolean customCourse,
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
        List<TimetableMeetingResponse> meetings
) {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static TimetableCourseResponse from(
            TimetableCourse timetableCourse,
            List<CourseSchedule> schedules,
            List<String> eligibleDepartmentNames
    ) {
        CourseOffering offering = timetableCourse.getCourseOffering();
        return new TimetableCourseResponse(
                timetableCourse.getId(),
                false,
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
                schedules.stream().map(TimetableMeetingResponse::from).toList()
        );
    }

    public static TimetableCourseResponse fromCustom(TimetableCourse timetableCourse) {
        String scheduleText = "%s %s~%s".formatted(
                timetableCourse.getCustomDayOfWeek(),
                timetableCourse.getCustomStartTime().format(TIME_FORMATTER),
                timetableCourse.getCustomEndTime().format(TIME_FORMATTER)
        );
        return new TimetableCourseResponse(
                timetableCourse.getId(),
                true,
                null,
                null,
                timetableCourse.getCustomCourseName(),
                null,
                timetableCourse.getCustomCredit(),
                false,
                null,
                List.of(),
                null,
                scheduleText,
                null,
                List.of(TimetableMeetingResponse.custom(timetableCourse))
        );
    }
}
