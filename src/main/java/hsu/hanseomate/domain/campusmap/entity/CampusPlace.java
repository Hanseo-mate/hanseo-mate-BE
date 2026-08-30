package hsu.hanseomate.domain.campusmap.entity;

import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.domain.campusmap.type.CampusPlaceCategory;
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
        name = "campus_places",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_campus_place_campus_name_key",
                columnNames = {"campus_code", "place_name_key"}
        ),
        indexes = @Index(
                name = "ix_campus_place_campus",
                columnList = "campus_code"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampusPlace extends BaseTimeEntity {

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

    @Column(name = "place_name", nullable = false, length = 255)
    private String placeName;

    @Column(name = "place_name_key", nullable = false, length = 255)
    private String placeNameKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 40)
    private CampusPlaceCategory category;

    @Column(name = "one_line_description", length = 255)
    private String oneLineDescription;

    @Column(length = 255)
    private String address;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(nullable = false, precision = 12, scale = 9)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 12, scale = 9)
    private BigDecimal longitude;

    private CampusPlace(
            CampusCode campusCode,
            String placeName,
            String placeNameKey,
            BigDecimal latitude,
            BigDecimal longitude,
            CampusPlaceCategory category,
            String oneLineDescription,
            String address,
            String imageUrl
    ) {
        update(
                campusCode,
                placeName,
                placeNameKey,
                latitude,
                longitude,
                category,
                oneLineDescription,
                address,
                imageUrl
        );
    }

    public static CampusPlace create(
            CampusCode campusCode,
            String placeName,
            String placeNameKey,
            BigDecimal latitude,
            BigDecimal longitude,
            CampusPlaceCategory category,
            String oneLineDescription,
            String address,
            String imageUrl
    ) {
        return new CampusPlace(
                campusCode,
                placeName,
                placeNameKey,
                latitude,
                longitude,
                category,
                oneLineDescription,
                address,
                imageUrl
        );
    }

    public void update(
            CampusCode campusCode,
            String placeName,
            String placeNameKey,
            BigDecimal latitude,
            BigDecimal longitude,
            CampusPlaceCategory category,
            String oneLineDescription,
            String address,
            String imageUrl
    ) {
        this.campusCode = Objects.requireNonNull(campusCode);
        this.placeName = requiredText(placeName, "placeName");
        this.placeNameKey = requiredText(placeNameKey, "placeNameKey");
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
        this.category = Objects.requireNonNull(category);
        this.oneLineDescription = optionalText(oneLineDescription);
        this.address = address;
        this.imageUrl = optionalText(imageUrl);
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
