package hsu.hanseomate.domain.schoolcalendar.entity;

import hsu.hanseomate.domain.academiccalendar.type.AcademicEventType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

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
        uniqueConstraints = @UniqueConstraint(name = "uk_school_calendar_source",
                columnNames = {"source", "academic_year", "semester", "source_event_id"}),
        indexes = @Index(
                name = "idx_school_calendar_events_dates",
                columnList = "start_date,end_date,id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolCalendarEvent extends BaseTimeEntity {

    @Column(nullable = false, length = 20)
    private String source = "MANUAL";
    @Column(name = "source_event_id", length = 100)
    private String sourceEventId;
    @Column(name = "academic_year")
    private Integer academicYear;
    private Integer semester;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 30)
    private AcademicEventType eventType =
            AcademicEventType.OTHER;
    @Column(name = "source_url", length = 1000)
    private String sourceUrl;
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
    @Column(nullable = false)
    private boolean active = true;

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
