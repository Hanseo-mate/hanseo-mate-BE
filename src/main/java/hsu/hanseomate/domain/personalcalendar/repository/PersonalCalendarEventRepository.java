package hsu.hanseomate.domain.personalcalendar.repository;

import hsu.hanseomate.domain.personalcalendar.entity.PersonalCalendarEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalCalendarEventRepository
        extends JpaRepository<PersonalCalendarEvent, Long> {

    List<PersonalCalendarEvent>
            findAllByOwner_IdOrderByStartDateAscEndDateAscIdAsc(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from PersonalCalendarEvent event
            where event.id = :calendarId
              and event.owner.id = :ownerId
            """)
    Optional<PersonalCalendarEvent> findOwnedByIdForUpdate(
            @Param("calendarId") Long calendarId,
            @Param("ownerId") Long ownerId
    );
}
