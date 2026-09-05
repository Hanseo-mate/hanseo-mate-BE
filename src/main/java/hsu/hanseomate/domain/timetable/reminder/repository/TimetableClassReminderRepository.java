package hsu.hanseomate.domain.timetable.reminder.repository;

import hsu.hanseomate.domain.timetable.reminder.entity.TimetableClassReminder;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableClassReminderRepository extends JpaRepository<TimetableClassReminder, Long> {

    boolean existsByTimetableCourseIdAndStartsAt(Long timetableCourseId, LocalDateTime startsAt);
}
