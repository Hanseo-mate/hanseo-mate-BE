package hsu.hanseomate.domain.course.dto;

import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import java.util.List;

public record CourseScheduleResponse(
        DayOfWeek dayOfWeek,
        List<Integer> periods,
        ClassroomResponse classroom
) {
    public static CourseScheduleResponse from(CourseSchedule schedule) {
        return new CourseScheduleResponse(
                schedule.getDayOfWeek(),
                schedule.getPeriods(),
                ClassroomResponse.from(schedule.getClassroom())
        );
    }
}
