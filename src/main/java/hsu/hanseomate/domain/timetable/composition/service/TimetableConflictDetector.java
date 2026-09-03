package hsu.hanseomate.domain.timetable.composition.service;

import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.support.CoursePeriodTimePolicy;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TimetableConflictDetector {

    public boolean conflicts(
            List<CourseSchedule> candidateSchedules,
            List<CourseSchedule> existingSchedules
    ) {
        for (CourseSchedule candidate : candidateSchedules) {
            for (CourseSchedule existing : existingSchedules) {
                if (sameDay(candidate, existing) && periodsOverlap(candidate, existing)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean conflicts(
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            List<CourseSchedule> schedules
    ) {
        return schedules.stream()
                .filter(schedule -> schedule.getDayOfWeek() == dayOfWeek)
                .flatMap(schedule -> schedule.getPeriods().stream())
                .map(CoursePeriodTimePolicy::find)
                .flatMap(java.util.Optional::stream)
                .anyMatch(period -> overlaps(
                        startTime,
                        endTime,
                        period.startTime(),
                        period.endTime()
                ));
    }

    public boolean conflicts(
            DayOfWeek firstDay,
            LocalTime firstStart,
            LocalTime firstEnd,
            DayOfWeek secondDay,
            LocalTime secondStart,
            LocalTime secondEnd
    ) {
        return firstDay == secondDay
                && overlaps(firstStart, firstEnd, secondStart, secondEnd);
    }

    private boolean sameDay(CourseSchedule first, CourseSchedule second) {
        return first.getDayOfWeek() == second.getDayOfWeek();
    }

    private boolean periodsOverlap(CourseSchedule first, CourseSchedule second) {
        Set<Integer> periods = new HashSet<>(first.getPeriods());
        return second.getPeriods().stream().anyMatch(periods::contains);
    }

    private boolean overlaps(
            LocalTime firstStart,
            LocalTime firstEnd,
            LocalTime secondStart,
            LocalTime secondEnd
    ) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }
}
