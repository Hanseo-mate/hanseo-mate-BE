package hsu.hanseomate.domain.courseenrichment.crossmajor.entity;

import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionRuleData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@org.hibernate.annotations.Immutable
@Table(
        name = "cross_major_rule_contents",
        indexes = @Index(name = "ix_cross_major_content_name", columnList = "course_name_key")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrossMajorRuleContent {

    @Id
    @Column(name = "rule_key", nullable = false, updatable = false, length = 64)
    private String ruleKey;

    @Column(name = "student_college_name", nullable = false, updatable = false, length = 255)
    private String studentCollegeName;

    @Column(name = "student_department_name", nullable = false, updatable = false, length = 255)
    private String studentDepartmentName;

    @Column(name = "student_major_name", nullable = false, updatable = false, length = 255)
    private String studentMajorName;

    @Column(name = "offering_college_name", nullable = false, updatable = false, length = 255)
    private String offeringCollegeName;

    @Column(name = "offering_department_name", nullable = false, updatable = false, length = 255)
    private String offeringDepartmentName;

    @Column(name = "offering_major_name", nullable = false, updatable = false, length = 255)
    private String offeringMajorName;

    @Column(name = "offering_department_key", nullable = false, updatable = false, length = 255)
    private String offeringDepartmentKey;

    @Column(name = "offering_major_key", nullable = false, updatable = false, length = 255)
    private String offeringMajorKey;

    @Column(name = "course_code", nullable = false, updatable = false, length = 7)
    private String courseCode;

    @Column(name = "course_name_snapshot", nullable = false, updatable = false, length = 255)
    private String courseName;

    @Column(name = "course_name_key", nullable = false, updatable = false, length = 255)
    private String courseNameKey;

    @Column(name = "effective_year", nullable = false, updatable = false)
    private int effectiveYear;

    @Column(name = "effective_semester", nullable = false, updatable = false)
    private int effectiveSemester;

    private CrossMajorRuleContent(
            CrossMajorRecognitionRuleData data
    ) {
        this.ruleKey = data.ruleKey();
        this.studentCollegeName = data.studentCollegeName();
        this.studentDepartmentName = data.studentDepartmentName();
        this.studentMajorName = data.studentMajorName();
        this.offeringCollegeName = data.offeringCollegeName();
        this.offeringDepartmentName = data.offeringDepartmentName();
        this.offeringMajorName = data.offeringMajorName();
        this.offeringDepartmentKey = data.offeringDepartmentKey();
        this.offeringMajorKey = data.offeringMajorKey();
        this.courseCode = data.courseCode();
        this.courseName = data.courseName();
        this.courseNameKey = data.courseNameKey();
        this.effectiveYear = data.effectiveYear();
        this.effectiveSemester = data.effectiveSemester();
    }

    public static CrossMajorRuleContent create(
            CrossMajorRecognitionRuleData data
    ) {
        return new CrossMajorRuleContent(data);
    }
}
