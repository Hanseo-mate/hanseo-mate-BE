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
        name = "equivalent_course_groups",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_equivalent_group_serial",
                        columnNames = {"import_history_id", "source_serial"}
                ),
                @UniqueConstraint(
                        name = "uk_equivalent_group_order",
                        columnNames = {"import_history_id", "group_order"}
                )
        },
        indexes = @Index(
                name = "ix_equivalent_group_history",
                columnList = "import_history_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquivalentCourseGroup {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_history_id", nullable = false)
    private EquivalentCourseImportHistory importHistory;

    @Column(name = "source_serial", nullable = false)
    private int sourceSerial;

    @Column(name = "group_order", nullable = false)
    private int groupOrder;

    @Column(name = "source_sheet", nullable = false, length = 255)
    private String sourceSheet;

    @Column(name = "source_start_row", nullable = false)
    private int sourceStartRow;

    @Column(name = "source_end_row", nullable = false)
    private int sourceEndRow;

    private EquivalentCourseGroup(
            EquivalentCourseImportHistory importHistory,
            int sourceSerial,
            int groupOrder,
            String sourceSheet,
            int sourceStartRow,
            int sourceEndRow
    ) {
        this.id = UUID.randomUUID();
        this.importHistory = importHistory;
        this.sourceSerial = sourceSerial;
        this.groupOrder = groupOrder;
        this.sourceSheet = sourceSheet;
        this.sourceStartRow = sourceStartRow;
        this.sourceEndRow = sourceEndRow;
    }

    public static EquivalentCourseGroup create(
            EquivalentCourseImportHistory importHistory,
            int sourceSerial,
            int groupOrder,
            String sourceSheet,
            int sourceStartRow,
            int sourceEndRow
    ) {
        return new EquivalentCourseGroup(
                importHistory,
                sourceSerial,
                groupOrder,
                sourceSheet,
                sourceStartRow,
                sourceEndRow
        );
    }
}
