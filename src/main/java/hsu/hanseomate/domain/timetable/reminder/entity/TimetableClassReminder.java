package hsu.hanseomate.domain.timetable.reminder.entity;

import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Entity
@Table(name = "timetable_class_reminders", uniqueConstraints = @UniqueConstraint(
        name = "uk_timetable_class_reminder_occurrence",
        columnNames = {"timetable_course_id", "starts_at"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableClassReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_class_reminder_timetable_course"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TimetableCourse timetableCourse;

    /** 실제 수업 날짜/시각 (Asia/Seoul). 매주 같은 수업도 날짜가 다르면 새 알림입니다. */
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    public static TimetableClassReminder create(TimetableCourse entry, LocalDateTime startsAt) {
        TimetableClassReminder reminder = new TimetableClassReminder();
        reminder.timetableCourse = entry;
        reminder.startsAt = startsAt;
        return reminder;
    }
}
