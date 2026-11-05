package hsu.hanseomate.domain.schoolcalendar.repository;

import hsu.hanseomate.domain.schoolcalendar.entity.SchoolCalendarEvent;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolCalendarEventRepository
        extends JpaRepository<SchoolCalendarEvent, Long> {

    List<SchoolCalendarEvent> findAllByOrderByStartDateAscEndDateAscIdAsc();

    List<SchoolCalendarEvent> findBySourceAndAcademicYearAndSemesterAndActiveTrueOrderByStartDateAsc(
            String source, Integer academicYear, Integer semester);

    @Query("""
            select e from SchoolCalendarEvent e
            where e.active = true and e.source = 'MANUAL'
            and e.academicYear is null and e.semester is null
            and e.eventType = hsu.hanseomate.domain.academiccalendar.type.AcademicEventType.OTHER
            and e.startDate >= :fromDate and e.startDate < :untilDate
            order by e.startDate, e.endDate, e.id
            """)
    List<SchoolCalendarEvent> findLegacyAcademicCandidates(
            @Param("fromDate") LocalDate fromDate, @Param("untilDate") LocalDate untilDate);

    @Query("""
            select e from SchoolCalendarEvent e where e.active = true
            and (:year is null or e.academicYear = :year)
            and (:semester is null or e.semester = :semester)
            order by e.startDate, e.endDate, e.id
            """)
    List<SchoolCalendarEvent> findVisible(@Param("year") Integer year, @Param("semester") Integer semester);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from SchoolCalendarEvent event where event.id = :calendarId")
    Optional<SchoolCalendarEvent> findByIdForUpdate(
            @Param("calendarId") Long calendarId
    );
}
