package hsu.hanseomate.domain.courseimport.entity;

import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "course_import_issues")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseImportIssue {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_history_id", nullable = false)
    private CourseImportHistory importHistory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueSeverity severity;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "sheet_name", length = 255)
    private String sheetName;

    @Column(name = "issue_row_number")
    private Integer rowNumber;

    @Column(name = "field_name", length = 100)
    private String field;

    @Column(name = "raw_value", length = 2000)
    private String rawValue;

    private CourseImportIssue(
            CourseImportHistory importHistory,
            IssueSeverity severity,
            String code,
            String message,
            String sheetName,
            Integer rowNumber,
            String field,
            String rawValue
    ) {
        this.id = UUID.randomUUID();
        this.importHistory = importHistory;
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.sheetName = sheetName;
        this.rowNumber = rowNumber;
        this.field = field;
        this.rawValue = rawValue;
    }

    public static CourseImportIssue create(
            CourseImportHistory importHistory,
            IssueSeverity severity,
            String code,
            String message,
            String sheetName,
            Integer rowNumber,
            String field,
            String rawValue
    ) {
        return new CourseImportIssue(
                importHistory, severity, code, message, sheetName, rowNumber, field, rawValue
        );
    }
}
