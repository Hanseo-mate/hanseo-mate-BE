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

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(nullable = false, precision = 12, scale = 9)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 12, scale = 9)
    private BigDecimal longitude;
}
