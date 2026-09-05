package hsu.hanseomate.domain.timetable.reminder.service;

import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.push.service.NotificationService;
import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import hsu.hanseomate.domain.timetable.reminder.entity.TimetableClassReminder;
import hsu.hanseomate.domain.timetable.reminder.repository.TimetableClassReminderRepository;
import hsu.hanseomate.domain.timetable.reminder.service.TimetableReminderQueryService.Candidate;
import hsu.hanseomate.domain.timetable.reminder.support.TimetableReminderPolicy;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimetableReminderEnqueueService {

    private final TimetableRepository timetableRepository;
    private final TimetableCourseRepository timetableCourseRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final TimetableClassReminderRepository reminderRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public boolean enqueue(Candidate candidate) {
        // 시간표 추가/삭제와 동일한 부모 행 잠금. 여러 서버가 조회해도 한 번만 생성합니다.
        Timetable timetable = timetableRepository.findByIdForUpdate(candidate.timetableId())
                .orElse(null);
        if (timetable == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(TimetableReminderPolicy.ZONE));
        if (!TimetableReminderPolicy.isDue(candidate.startsAt(), now)
                || timetable.getAcademicYear() != candidate.startsAt().getYear()
                || timetable.getSemester() != TimetableReminderPolicy.semester(candidate.startsAt().toLocalDate())) {
            return false;
        }
        TimetableCourse entry = timetableCourseRepository.findById(candidate.timetableCourseId())
                .orElse(null);
        if (entry == null || !entry.getTimetable().getId().equals(timetable.getId())
                || reminderRepository.existsByTimetableCourseIdAndStartsAt(entry.getId(), candidate.startsAt())) {
            return false;
        }
        List<CourseSchedule> schedules = entry.isCustomCourse() ? List.of()
                : courseScheduleRepository.findAllForCourses(List.of(entry.getCourseOffering().getCourse().getId()));
        if (!TimetableReminderPolicy.startTimes(entry, schedules, candidate.startsAt().toLocalDate())
                .contains(candidate.startsAt().toLocalTime())) {
            return false;
        }
        String courseName = entry.isCustomCourse() ? entry.getCustomCourseName()
                : entry.getCourseOffering().getCourseName();
        reminderRepository.save(TimetableClassReminder.create(entry, candidate.startsAt()));
        notificationService.enqueueTimetableClassReminder(
                timetable.getOwnerId(), timetable.getId(), entry.getId(),
                timetable.getAcademicYear(), timetable.getSemester(), courseName,
                candidate.startsAt().atZone(TimetableReminderPolicy.ZONE).toOffsetDateTime()
        );
        // 발송 이력, 개인 알림함, Outbox는 함께 commit/rollback됩니다.
        return true;
    }
}
