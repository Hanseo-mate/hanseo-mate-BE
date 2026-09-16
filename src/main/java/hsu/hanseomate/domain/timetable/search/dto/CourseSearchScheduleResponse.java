package hsu.hanseomate.domain.timetable.search.dto;

import hsu.hanseomate.domain.course.entity.Classroom;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import java.util.List;

public record CourseSearchScheduleResponse(
        DayOfWeek dayOfWeek,
        List<Integer> periods,
        String buildingName,
        String roomNumber
) {
    public static CourseSearchScheduleResponse from(CourseSchedule schedule) {
        Classroom classroom = schedule.getClassroom();
        return new CourseSearchScheduleResponse(
                schedule.getDayOfWeek(),
                schedule.getPeriods(),
                classroom == null ? null : classroom.getBuildingName(),
                classroom == null ? null : classroom.getRoomNumber()
        );
    }
}
