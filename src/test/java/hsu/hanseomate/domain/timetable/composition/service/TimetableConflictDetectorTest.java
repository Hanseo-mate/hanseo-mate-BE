package hsu.hanseomate.domain.timetable.composition.service;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimetableConflictDetectorTest {

    private final TimetableConflictDetector detector = new TimetableConflictDetector();

    @Test
    void detectsOverlappingPeriodOnSameDay() {
        assertThat(detector.conflicts(
                List.of(schedule(DayOfWeek.MONDAY, 1, 2)),
                List.of(schedule(DayOfWeek.MONDAY, 2, 3))
        )).isTrue();
    }

    @Test
    void doesNotConflictOnDifferentDays() {
        assertThat(detector.conflicts(
                List.of(schedule(DayOfWeek.MONDAY, 1, 2)),
                List.of(schedule(DayOfWeek.TUESDAY, 1, 2))
        )).isFalse();
    }

    @Test
    void adjacentPeriodsDoNotConflict() {
        assertThat(detector.conflicts(
                List.of(schedule(DayOfWeek.MONDAY, 0, 1)),
                List.of(schedule(DayOfWeek.MONDAY, 2, 3))
        )).isFalse();
    }

    @Test
    void detectsConflictWhenAnyOneOfMultipleMeetingsOverlaps() {
        assertThat(detector.conflicts(
                List.of(
                        schedule(DayOfWeek.MONDAY, 1, 2),
                        schedule(DayOfWeek.WEDNESDAY, 4, 5)
                ),
                List.of(schedule(DayOfWeek.WEDNESDAY, 5, 6))
        )).isTrue();
    }

    @Test
    void coursesWithoutStructuredMeetingsDoNotConflict() {
        assertThat(detector.conflicts(
                List.of(),
                List.of(schedule(DayOfWeek.MONDAY, 1, 2))
        )).isFalse();
    }

    private CourseSchedule schedule(DayOfWeek dayOfWeek, Integer... periods) {
        return CourseSchedule.create(null, 0, dayOfWeek, List.of(periods), null);
    }
}
