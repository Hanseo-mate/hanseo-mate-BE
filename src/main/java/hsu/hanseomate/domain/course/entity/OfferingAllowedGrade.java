package hsu.hanseomate.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "offering_allowed_grades", uniqueConstraints = {
        @UniqueConstraint(name = "uk_course_allowed_grade", columnNames = {"course_id", "grade"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferingAllowedGrade {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private int grade;

    private OfferingAllowedGrade(Course course, int grade) {
        this.id = UUID.randomUUID();
        this.course = course;
        this.grade = grade;
    }

    public static OfferingAllowedGrade create(CourseOffering offering, int grade) {
        return create(offering.getCourse(), grade);
    }

    public static OfferingAllowedGrade create(Course course, int grade) {
        return new OfferingAllowedGrade(course, grade);
    }
}
