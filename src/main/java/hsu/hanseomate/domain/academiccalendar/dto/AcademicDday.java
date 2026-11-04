package hsu.hanseomate.domain.academiccalendar.dto;
import java.time.LocalDate;
public record AcademicDday(String type, LocalDate targetDate, LocalDate endDate, long daysRemaining) {}
