package hsu.hanseomate.domain.calendar.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "student_council_calendar_events",
        indexes = @Index(
                name = "idx_calendar_event_dates",
                columnList = "start_date,end_date,id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    private CalendarEvent(
            LocalDate startDate,
            LocalDate endDate,
            String title,
            String content
    ) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.title = title;
        this.content = content;
    }

    public static CalendarEvent create(
            LocalDate startDate,
            LocalDate endDate,
            String title,
            String content
    ) {
        return new CalendarEvent(startDate, endDate, title, content);
    }

    public void update(
            LocalDate startDate,
            LocalDate endDate,
            String title,
            String content
    ) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.title = title;
        this.content = content;
    }
}
