package hsu.hanseomate.domain.academiccalendar.entity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.*;
@Entity
@Table(name = "academic_holiday_months")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HolidayMonth {
    @Id @Column(name = "month_key", length = 7) private String monthKey;
    @Column(name = "holiday_dates", nullable = false, length = 400) private String holidayDates;
    @Column(name = "synced_at", nullable = false) private LocalDateTime syncedAt;
    public static HolidayMonth snapshot(YearMonth month, Set<LocalDate> dates, LocalDateTime now) {
        HolidayMonth value = new HolidayMonth();
        value.monthKey = month.toString();
        value.holidayDates = String.join(",", dates.stream().sorted().map(LocalDate::toString).toList());
        value.syncedAt = now;
        return value;
    }
    public Set<LocalDate> dates() {
        if (holidayDates.isBlank()) return Set.of();
        return new HashSet<>(Arrays.stream(holidayDates.split(",")).map(LocalDate::parse).toList());
    }
}
