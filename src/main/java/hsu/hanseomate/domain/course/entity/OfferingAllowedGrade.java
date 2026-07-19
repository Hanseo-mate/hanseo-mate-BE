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
        @UniqueConstraint(name = "uk_offering_allowed_grade", columnNames = {"offering_id", "grade"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferingAllowedGrade {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Column(nullable = false)
    private int grade;

    private OfferingAllowedGrade(CourseOffering offering, int grade) {
        this.id = UUID.randomUUID();
        this.offering = offering;
        this.grade = grade;
    }

    public static OfferingAllowedGrade create(CourseOffering offering, int grade) {
        return new OfferingAllowedGrade(offering, grade);
    }
}
