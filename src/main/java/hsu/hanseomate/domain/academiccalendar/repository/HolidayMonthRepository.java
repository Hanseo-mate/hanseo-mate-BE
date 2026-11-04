package hsu.hanseomate.domain.academiccalendar.repository;
import hsu.hanseomate.domain.academiccalendar.entity.HolidayMonth;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HolidayMonthRepository extends JpaRepository<HolidayMonth, String> {}
