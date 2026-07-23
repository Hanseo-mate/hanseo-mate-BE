package hsu.hanseomate.domain.course.entity;

import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "course_schedules",
        indexes = @Index(
                name = "ix_schedule_offering_order",
                columnList = "offering_id,schedule_order"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseSchedule {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Column(name = "schedule_order", nullable = false)
    private int scheduleOrder;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "day_of_week", nullable = false, length = 20)
    private DayOfWeek dayOfWeek;

    @Column(name = "periods_value", nullable = false, length = 200)
    private String periodsValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    private CourseSchedule(
            CourseOffering offering,
            int scheduleOrder,
            DayOfWeek dayOfWeek,
            List<Integer> periods,
            Classroom classroom
    ) {
        this.id = UUID.randomUUID();
        this.offering = offering;
        this.scheduleOrder = scheduleOrder;
        this.dayOfWeek = dayOfWeek;
        this.periodsValue = periods.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        this.classroom = classroom;
    }

    public static CourseSchedule create(
            CourseOffering offering,
            int scheduleOrder,
            DayOfWeek dayOfWeek,
            List<Integer> periods,
            Classroom classroom
    ) {
        return new CourseSchedule(offering, scheduleOrder, dayOfWeek, periods, classroom);
    }

    public List<Integer> getPeriods() {
        if (periodsValue.isBlank()) {
            return List.of();
        }
        return Arrays.stream(periodsValue.split(",")).map(Integer::valueOf).toList();
    }
}
