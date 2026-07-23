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
                name = "uk_offering_eligible_department",
                columnNames = {"offering_id", "department_name"}
        )
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferingEligibleDepartment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Column(name = "department_name", nullable = false, length = 255)
    private String departmentName;

    private OfferingEligibleDepartment(CourseOffering offering, String departmentName) {
        this.id = UUID.randomUUID();
        this.offering = offering;
        this.departmentName = departmentName;
    }

    public static OfferingEligibleDepartment create(CourseOffering offering, String departmentName) {
        return new OfferingEligibleDepartment(offering, departmentName);
    }
}
