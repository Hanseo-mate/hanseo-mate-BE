package hsu.hanseomate.domain.course.entity;

import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "offering_general_education",
        indexes = @Index(
                name = "ix_general_context_filter",
                columnList = "classification,area,delivery_provider"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfferingGeneralEducation {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false, unique = true)
    private CourseOffering offering;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private GeneralClassification classification;

    @Column(name = "classification_name", length = 255)
    private String classificationName;

    @Column(name = "category_code", length = 100)
    private String categoryCode;

    @Column(name = "category_name", length = 255)
    private String categoryName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private GeneralArea area;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "delivery_provider", nullable = false, length = 50)
    private DeliveryProvider deliveryProvider;

    @Column(name = "delivery_provider_name", length = 255)
    private String deliveryProviderName;

    @Lob
    @Column(name = "source_path_json", nullable = false, columnDefinition = "LONGTEXT")
    private String sourcePathJson;

    private OfferingGeneralEducation(
            CourseOffering offering,
            GeneralClassification classification,
            String classificationName,
            String categoryCode,
            String categoryName,
            GeneralArea area,
            DeliveryProvider deliveryProvider,
            String deliveryProviderName,
            String sourcePathJson
    ) {
        this.id = UUID.randomUUID();
        this.offering = offering;
        this.classification = classification;
        this.classificationName = classificationName;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.area = area;
        this.deliveryProvider = deliveryProvider;
        this.deliveryProviderName = deliveryProviderName;
        this.sourcePathJson = sourcePathJson;
    }

    public static OfferingGeneralEducation create(
            CourseOffering offering,
            GeneralClassification classification,
            String classificationName,
            String categoryCode,
            String categoryName,
            GeneralArea area,
            DeliveryProvider deliveryProvider,
            String deliveryProviderName,
            String sourcePathJson
    ) {
        return new OfferingGeneralEducation(
                offering, classification, classificationName, categoryCode, categoryName,
                area, deliveryProvider, deliveryProviderName, sourcePathJson
        );
    }
}
