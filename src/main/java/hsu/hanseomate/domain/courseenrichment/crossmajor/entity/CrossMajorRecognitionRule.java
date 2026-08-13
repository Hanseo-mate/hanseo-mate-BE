package hsu.hanseomate.domain.courseenrichment.crossmajor.entity;

import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionRuleData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(
        name = "cross_major_recognition_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cross_major_rule_history_key",
                columnNames = {"import_history_id", "rule_key"}
        ),
        indexes = @Index(
                name = "ix_cross_major_rule_history_code",
                columnList = "import_history_id,course_code"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrossMajorRecognitionRule {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_history_id", nullable = false)
    private CrossMajorRecognitionImportHistory importHistory;

    @Column(name = "rule_key", nullable = false, length = 64)
    private String ruleKey;

    @Column(name = "student_college_name", nullable = false, length = 255)
    private String studentCollegeName;

    @Column(name = "student_department_name", nullable = false, length = 255)
    private String studentDepartmentName;

    @Column(name = "student_major_name", nullable = false, length = 255)
    private String studentMajorName;

    @Column(name = "offering_college_name", nullable = false, length = 255)
    private String offeringCollegeName;

    @Column(name = "offering_department_name", nullable = false, length = 255)
    private String offeringDepartmentName;

    @Column(name = "offering_major_name", nullable = false, length = 255)
    private String offeringMajorName;

    @Column(name = "offering_department_key", nullable = false, length = 255)
    private String offeringDepartmentKey;

    @Column(name = "offering_major_key", nullable = false, length = 255)
    private String offeringMajorKey;

    @Column(name = "course_code", nullable = false, length = 7)
    private String courseCode;

    @Column(name = "course_name_snapshot", nullable = false, length = 255)
    private String courseName;

    @Column(name = "course_name_key", nullable = false, length = 255)
    private String courseNameKey;

    @Column(name = "effective_year", nullable = false)
    private int effectiveYear;

    @Column(name = "effective_semester", nullable = false)
    private int effectiveSemester;

    @Column(name = "source_sheet", nullable = false, length = 255)
    private String sourceSheet;

    @Column(name = "source_row", nullable = false)
    private int sourceRow;

    private CrossMajorRecognitionRule(
            CrossMajorRecognitionImportHistory importHistory,
            CrossMajorRecognitionRuleData data
    ) {
        this.id = UUID.randomUUID();
        this.importHistory = importHistory;
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
        this.sourceSheet = data.sourceSheet();
        this.sourceRow = data.sourceRow();
    }

    public static CrossMajorRecognitionRule create(
            CrossMajorRecognitionImportHistory importHistory,
            CrossMajorRecognitionRuleData data
    ) {
        return new CrossMajorRecognitionRule(importHistory, data);
    }
}
