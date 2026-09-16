package hsu.hanseomate.domain.schoolcalendar.entity;

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
        name = "school_calendar_events",
        indexes = @Index(
                name = "idx_school_calendar_events_dates",
                columnList = "start_date,end_date,id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolCalendarEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 500)
    private String title;

    private SchoolCalendarEvent(
            LocalDate startDate,
            LocalDate endDate,
            String title
    ) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.title = title;
    }

    public static SchoolCalendarEvent create(
            LocalDate startDate,
            LocalDate endDate,
            String title
    ) {
        return new SchoolCalendarEvent(startDate, endDate, title);
    }

    public void update(LocalDate startDate, LocalDate endDate, String title) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.title = title;
    }
}
