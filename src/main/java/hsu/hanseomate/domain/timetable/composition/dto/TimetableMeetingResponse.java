package hsu.hanseomate.domain.timetable.composition.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.support.CoursePeriodTimePolicy;
import hsu.hanseomate.domain.course.support.CoursePeriodTimePolicy.TimeRange;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.search.dto.ClassroomResponse;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public record TimetableMeetingResponse(
        DayOfWeek dayOfWeek,
        List<Integer> periods,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        ClassroomResponse classroom
) {
    public static TimetableMeetingResponse from(CourseSchedule schedule) {
        List<Integer> periods = schedule.getPeriods();
        Optional<TimeRange> timeRange = periods.isEmpty()
                ? Optional.empty()
                : CoursePeriodTimePolicy.findRange(
                        periods.get(0),
                        periods.get(periods.size() - 1)
                );
        return new TimetableMeetingResponse(
                schedule.getDayOfWeek(),
                periods,
                timeRange.map(TimeRange::startTime).orElse(null),
                timeRange.map(TimeRange::endTime).orElse(null),
                ClassroomResponse.from(schedule.getClassroom())
        );
    }

    public static TimetableMeetingResponse custom(TimetableCourse timetableCourse) {
        return new TimetableMeetingResponse(
                timetableCourse.getCustomDayOfWeek(),
                List.of(),
                timetableCourse.getCustomStartTime(),
                timetableCourse.getCustomEndTime(),
                null
        );
    }
}
