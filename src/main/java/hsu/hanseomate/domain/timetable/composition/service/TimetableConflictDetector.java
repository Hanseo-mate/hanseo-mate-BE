package hsu.hanseomate.domain.timetable.composition.service;

import hsu.hanseomate.domain.course.entity.CourseSchedule;
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

    private boolean sameDay(CourseSchedule first, CourseSchedule second) {
        return first.getDayOfWeek() == second.getDayOfWeek();
    }

    private boolean periodsOverlap(CourseSchedule first, CourseSchedule second) {
        Set<Integer> periods = new HashSet<>(first.getPeriods());
        return second.getPeriods().stream().anyMatch(periods::contains);
    }
}
