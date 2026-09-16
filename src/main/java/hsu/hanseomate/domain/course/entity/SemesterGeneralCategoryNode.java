package hsu.hanseomate.domain.course.entity;

import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralCategoryNodeType;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "semester_general_category_nodes",
        indexes = {
                @Index(
                        name = "ix_general_node_parent",
                        columnList = "semester_id,curriculum_type,parent_key"
                ),
                @Index(
                        name = "ix_general_node_order",
                        columnList = "semester_id,curriculum_type,sort_order"
                )
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_semester_curriculum_node",
                columnNames = {"semester_id", "curriculum_type", "node_key"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SemesterGeneralCategoryNode {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "curriculum_type", nullable = false, length = 30)
    private CurriculumType curriculumType;

    @Column(name = "node_key", nullable = false, length = 255)
    private String nodeKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "node_type", nullable = false, length = 30)
    private GeneralCategoryNodeType nodeType;

    @Column(length = 100)
    private String code;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "parent_key", length = 255)
    private String parentKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private GeneralClassification classification;

    @Column(name = "classification_name", length = 255)
    private String classificationName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private GeneralArea area;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "delivery_provider", length = 50)
    private DeliveryProvider deliveryProvider;

    @Column(name = "delivery_provider_name", length = 255)
    private String deliveryProviderName;

    @Lob
    @Column(name = "source_path_json", nullable = false, columnDefinition = "LONGTEXT")
    private String sourcePathJson;

    @Column(name = "source_sheet", nullable = false, length = 255)
    private String sourceSheet;

    @Column(name = "source_row", nullable = false)
    private int sourceRow;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private SemesterGeneralCategoryNode(
            Semester semester,
            CurriculumType curriculumType,
            String nodeKey,
            GeneralCategoryNodeType nodeType,
            String code,
            String name,
            String parentKey,
            GeneralClassification classification,
            String classificationName,
            GeneralArea area,
            DeliveryProvider deliveryProvider,
            String deliveryProviderName,
            String sourcePathJson,
            String sourceSheet,
            int sourceRow,
            int sortOrder
    ) {
        this.id = UUID.randomUUID();
        this.semester = semester;
        this.curriculumType = curriculumType;
        this.nodeKey = nodeKey;
        this.nodeType = nodeType;
        this.code = code;
        this.name = name;
        this.parentKey = parentKey;
        this.classification = classification;
        this.classificationName = classificationName;
        this.area = area;
        this.deliveryProvider = deliveryProvider;
        this.deliveryProviderName = deliveryProviderName;
        this.sourcePathJson = sourcePathJson;
        this.sourceSheet = sourceSheet;
        this.sourceRow = sourceRow;
        this.sortOrder = sortOrder;
    }

    public static SemesterGeneralCategoryNode create(
            Semester semester,
            CurriculumType curriculumType,
            String nodeKey,
            GeneralCategoryNodeType nodeType,
            String code,
            String name,
            String parentKey,
            GeneralClassification classification,
            String classificationName,
            GeneralArea area,
            DeliveryProvider deliveryProvider,
            String deliveryProviderName,
            String sourcePathJson,
            String sourceSheet,
            int sourceRow,
            int sortOrder
    ) {
        return new SemesterGeneralCategoryNode(
                semester, curriculumType, nodeKey, nodeType, code, name, parentKey,
                classification, classificationName, area, deliveryProvider,
                deliveryProviderName, sourcePathJson, sourceSheet, sourceRow, sortOrder
        );
    }
}
