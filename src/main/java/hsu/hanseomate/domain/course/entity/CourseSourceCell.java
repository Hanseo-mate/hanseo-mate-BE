package hsu.hanseomate.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "course_source_cells", uniqueConstraints = {
        @UniqueConstraint(name = "uk_offering_source_column", columnNames = {"offering_id", "column_index"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseSourceCell {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Column(name = "column_index", nullable = false)
    private int columnIndex;

    @Column(name = "header_name", nullable = false, length = 500)
    private String headerName;

    @Column(name = "canonical_field", length = 100)
    private String canonicalField;

    @Lob
    @Column(name = "cell_value", columnDefinition = "LONGTEXT")
    private String value;

    private CourseSourceCell(
            CourseOffering offering,
            int columnIndex,
            String headerName,
            String canonicalField,
            String value
    ) {
        this.id = UUID.randomUUID();
        this.offering = offering;
        this.columnIndex = columnIndex;
        this.headerName = headerName;
        this.canonicalField = canonicalField;
        this.value = value;
    }

    public static CourseSourceCell create(
            CourseOffering offering,
            int columnIndex,
            String headerName,
            String canonicalField,
            String value
    ) {
        return new CourseSourceCell(offering, columnIndex, headerName, canonicalField, value);
    }
}
