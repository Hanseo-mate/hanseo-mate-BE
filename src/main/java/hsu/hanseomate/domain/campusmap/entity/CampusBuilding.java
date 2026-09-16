package hsu.hanseomate.domain.campusmap.entity;

import hsu.hanseomate.domain.campusmap.support.CampusLocationNormalizer;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "campus_buildings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_campus_building_campus_name_key",
                        columnNames = {"campus_code", "canonical_name_key"}
                ),
                @UniqueConstraint(
                        name = "uk_campus_building_id_campus",
                        columnNames = {"id", "campus_code"}
                )
        },
        indexes = @Index(
                name = "ix_campus_building_campus",
                columnList = "campus_code"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampusBuilding extends BaseTimeEntity {

    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "campus_code", nullable = false, length = 20)
    private CampusCode campusCode;

    @Column(name = "canonical_name", nullable = false, length = 255)
    private String canonicalName;

    @Column(name = "canonical_name_key", nullable = false, length = 255)
    private String canonicalNameKey;

    @Column(nullable = false, precision = 12, scale = 9)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 12, scale = 9)
    private BigDecimal longitude;

    private CampusBuilding(
            CampusCode campusCode,
            String canonicalName,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.campusCode = Objects.requireNonNull(
                campusCode,
                "campusCode must not be null"
        );
        this.canonicalName = requiredText(canonicalName, "canonicalName");
        this.canonicalNameKey = CampusLocationNormalizer.normalize(canonicalName);
        if (this.canonicalNameKey.isEmpty()) {
            throw new IllegalArgumentException(
                    "canonicalName must contain a searchable character"
            );
        }
        this.latitude = coordinate(
                latitude,
                MIN_LATITUDE,
                MAX_LATITUDE,
                "latitude"
        );
        this.longitude = coordinate(
                longitude,
                MIN_LONGITUDE,
                MAX_LONGITUDE,
                "longitude"
        );
    }

    public static CampusBuilding create(
            CampusCode campusCode,
            String canonicalName,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        return new CampusBuilding(
                campusCode,
                canonicalName,
                latitude,
                longitude
        );
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static BigDecimal coordinate(
            BigDecimal value,
            BigDecimal minimum,
            BigDecimal maximum,
            String fieldName
    ) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(fieldName + " is out of range");
        }
        if (Math.max(value.stripTrailingZeros().scale(), 0) > 9) {
            throw new IllegalArgumentException(
                    fieldName + " must have at most 9 decimal places"
            );
        }
        return value;
    }
}
