package hsu.hanseomate.domain.timetable.reminder.support;

import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.support.CourseCyberPolicy;
import hsu.hanseomate.domain.course.support.CoursePeriodTimePolicy;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class TimetableReminderPolicy {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final int MINUTES_BEFORE = 60;
    public static final int CATCH_UP_MINUTES = 2;
    public static final int EXPIRY_MINUTES = 5;

    private TimetableReminderPolicy() {
    }

    // 현재 캠퍼스맵과 동일한 학기 구분. 개강/종강일 및 휴강 정보는 별도 연동 대상입니다.
    public static int semester(LocalDate classDate) {
        return classDate.getMonthValue() <= 6 ? 1 : 2;
    }

    public static boolean isDue(LocalDateTime startsAt, LocalDateTime now) {
        LocalDateTime dueAt = startsAt.minusMinutes(MINUTES_BEFORE);
        return !dueAt.isAfter(now) && dueAt.isAfter(now.minusMinutes(CATCH_UP_MINUTES));
    }

    public static List<LocalTime> startTimes(
            TimetableCourse entry,
            List<CourseSchedule> schedules,
            LocalDate classDate
    ) {
        DayOfWeek day = DayOfWeek.valueOf(classDate.getDayOfWeek().name());
        if (entry.isCustomCourse()) {
            return entry.getCustomDayOfWeek() == day && entry.getCustomStartTime() != null
                    ? List.of(entry.getCustomStartTime())
                    : List.of();
        }
        if (!entry.getCourseOffering().isActive()
                || CourseCyberPolicy.isCyber(entry.getCourseOffering())) {
            return List.of();
        }

        // 같은 요일의 분리된 schedule 행도 합쳐 연속 교시의 중간에 알리지 않습니다.
        List<Integer> periods = schedules.stream()
                .filter(schedule -> schedule.getDayOfWeek() == day)
                .flatMap(schedule -> schedule.getPeriods().stream())
                .distinct()
                .sorted()
                .toList();
        List<LocalTime> starts = new ArrayList<>();
        Integer previous = null;
        for (int period : periods) {
            if (previous == null || period != previous + 1) {
                CoursePeriodTimePolicy.find(period)
                        .ifPresent(time -> starts.add(time.startTime()));
            }
            previous = period;
        }
        return List.copyOf(starts);
    }
}
