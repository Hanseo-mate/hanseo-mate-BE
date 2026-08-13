package hsu.hanseomate.domain.courseenrichment.equivalence.entity;

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
        name = "equivalent_course_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_equivalent_member_code",
                columnNames = {"import_history_id", "course_code"}
        ),
        indexes = @Index(
                name = "ix_equivalent_member_group_order",
                columnList = "group_id,member_order"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquivalentCourseMember {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_history_id", nullable = false)
    private EquivalentCourseImportHistory importHistory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private EquivalentCourseGroup group;

    @Column(name = "course_code", nullable = false, length = 7)
    private String courseCode;

    @Column(name = "course_name", nullable = false, length = 255)
    private String courseName;

    @Column(name = "source_sheet", nullable = false, length = 255)
    private String sourceSheet;

    @Column(name = "source_row", nullable = false)
    private int sourceRow;

    @Column(name = "member_order", nullable = false)
    private int memberOrder;

    private EquivalentCourseMember(
            EquivalentCourseImportHistory importHistory,
            EquivalentCourseGroup group,
            String courseCode,
            String courseName,
            String sourceSheet,
            int sourceRow,
            int memberOrder
    ) {
        this.id = UUID.randomUUID();
        this.importHistory = importHistory;
        this.group = group;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.sourceSheet = sourceSheet;
        this.sourceRow = sourceRow;
        this.memberOrder = memberOrder;
    }

    public static EquivalentCourseMember create(
            EquivalentCourseImportHistory importHistory,
            EquivalentCourseGroup group,
            String courseCode,
            String courseName,
            String sourceSheet,
            int sourceRow,
            int memberOrder
    ) {
        return new EquivalentCourseMember(
                importHistory,
                group,
                courseCode,
                courseName,
                sourceSheet,
                sourceRow,
                memberOrder
        );
    }
}
