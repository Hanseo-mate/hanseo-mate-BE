package hsu.hanseomate.domain.timetable.composition.entity;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.gradecalculator.type.ExpectedGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(
        name = "timetable_courses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_timetable_course_offering",
                columnNames = {"timetable_id", "course_offering_id"}
        ),
        indexes = {
                @Index(name = "ix_timetable_course_timetable", columnList = "timetable_id"),
                @Index(name = "ix_timetable_course_offering", columnList = "course_offering_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timetable_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Timetable timetable;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CourseOffering courseOffering;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "expected_grade", length = 20)
    private ExpectedGrade expectedGrade;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private TimetableCourse(Timetable timetable, CourseOffering courseOffering) {
        this.timetable = timetable;
        this.courseOffering = courseOffering;
    }

    public static TimetableCourse create(Timetable timetable, CourseOffering courseOffering) {
        return new TimetableCourse(timetable, courseOffering);
    }

    public void updateExpectedGrade(ExpectedGrade expectedGrade) {
        this.expectedGrade = expectedGrade;
    }
}
