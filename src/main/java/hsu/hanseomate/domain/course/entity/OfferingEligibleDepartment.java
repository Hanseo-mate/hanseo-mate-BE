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
@Table(name = "offering_eligible_departments", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_course_eligible_department",
                columnNames = {"course_id", "department_name"}
        )
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferingEligibleDepartment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "department_name", nullable = false, length = 255)
    private String departmentName;

    private OfferingEligibleDepartment(Course course, String departmentName) {
        this.id = UUID.randomUUID();
        this.course = course;
        this.departmentName = departmentName;
    }

    public static OfferingEligibleDepartment create(CourseOffering offering, String departmentName) {
        return create(offering.getCourse(), departmentName);
    }

    public static OfferingEligibleDepartment create(Course course, String departmentName) {
        return new OfferingEligibleDepartment(course, departmentName);
    }
}
