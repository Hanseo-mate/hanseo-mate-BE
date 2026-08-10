package hsu.hanseomate.domain.schoolcalendar.repository;

import hsu.hanseomate.domain.schoolcalendar.entity.SchoolCalendarEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolCalendarEventRepository
        extends JpaRepository<SchoolCalendarEvent, Long> {

    List<SchoolCalendarEvent> findAllByOrderByStartDateAscEndDateAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from SchoolCalendarEvent event where event.id = :calendarId")
    Optional<SchoolCalendarEvent> findByIdForUpdate(
            @Param("calendarId") Long calendarId
    );
}
