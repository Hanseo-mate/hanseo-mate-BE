package hsu.hanseomate.domain.calendar.repository;

import hsu.hanseomate.domain.calendar.entity.CalendarEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findAllByOrderByStartDateAscEndDateAscIdAsc();
}
