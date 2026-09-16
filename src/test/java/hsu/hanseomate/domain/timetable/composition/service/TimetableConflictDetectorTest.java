package hsu.hanseomate.domain.timetable.composition.service;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.course.entity.Course;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import java.time.LocalTime;
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

    @Test
    void customTimeConflictsWithOverlappingRegisteredPeriod() {
        assertThat(detector.conflicts(
                DayOfWeek.MONDAY,
                LocalTime.of(9, 45),
                LocalTime.of(10, 15),
                List.of(schedule(DayOfWeek.MONDAY, 1, 2))
        )).isTrue();
    }

    @Test
    void customTimeCanBeAdjacentToRegisteredPeriod() {
        assertThat(detector.conflicts(
                DayOfWeek.MONDAY,
                LocalTime.of(10, 30),
                LocalTime.of(11, 0),
                List.of(schedule(DayOfWeek.MONDAY, 1, 2))
        )).isFalse();
    }

    @Test
    void customTimesConflictOnlyWhenDayAndTimeOverlap() {
        assertThat(detector.conflicts(
                DayOfWeek.WEDNESDAY,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                DayOfWeek.WEDNESDAY,
                LocalTime.of(13, 30),
                LocalTime.of(14, 30)
        )).isTrue();
        assertThat(detector.conflicts(
                DayOfWeek.WEDNESDAY,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                DayOfWeek.THURSDAY,
                LocalTime.of(13, 30),
                LocalTime.of(14, 30)
        )).isFalse();
    }

    private CourseSchedule schedule(DayOfWeek dayOfWeek, Integer... periods) {
        return CourseSchedule.create((Course) null, 0, dayOfWeek, List.of(periods), null);
    }
}
