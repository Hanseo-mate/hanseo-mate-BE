package hsu.hanseomate.domain.timetable.reminder.service;

import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.reminder.support.TimetableReminderPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimetableReminderQueryService {

    public static final int BATCH_SIZE = 200;

    private final TimetableCourseRepository timetableCourseRepository;
    private final CourseScheduleRepository courseScheduleRepository;

    public CandidatePage findCandidates(LocalDate classDate, LocalDateTime now, long afterId) {
        List<TimetableCourse> entries = timetableCourseRepository.findReminderEntries(
                classDate.getYear(),
                TimetableReminderPolicy.semester(classDate),
                DayOfWeek.valueOf(classDate.getDayOfWeek().name()),
                afterId,
                PageRequest.of(0, BATCH_SIZE)
        );
        List<UUID> courseIds = entries.stream()
                .filter(entry -> !entry.isCustomCourse())
                .map(entry -> entry.getCourseOffering().getCourse().getId())
                .distinct().toList();
        Map<UUID, List<CourseSchedule>> schedules = courseIds.isEmpty()
                ? Map.of()
                : courseScheduleRepository.findAllForCourses(courseIds).stream()
                        .collect(Collectors.groupingBy(schedule -> schedule.getCourse().getId()));

        List<Candidate> candidates = new ArrayList<>();
        for (TimetableCourse entry : entries) {
            List<CourseSchedule> meetings = entry.isCustomCourse() ? List.of()
                    : schedules.getOrDefault(entry.getCourseOffering().getCourse().getId(), List.of());
            TimetableReminderPolicy.startTimes(entry, meetings, classDate).stream()
                    .map(classDate::atTime)
                    .filter(startsAt -> TimetableReminderPolicy.isDue(startsAt, now))
                    .forEach(startsAt -> candidates.add(new Candidate(
                            entry.getTimetable().getId(), entry.getId(), startsAt)));
        }
        long lastId = entries.isEmpty() ? afterId : entries.get(entries.size() - 1).getId();
        return new CandidatePage(List.copyOf(candidates), lastId, entries.size() == BATCH_SIZE);
    }

    public record Candidate(Long timetableId, Long timetableCourseId, LocalDateTime startsAt) {
    }

    public record CandidatePage(List<Candidate> candidates, long lastId, boolean hasNext) {
    }
}
