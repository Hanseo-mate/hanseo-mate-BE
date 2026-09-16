package hsu.hanseomate.domain.campusmap.entity;

import hsu.hanseomate.domain.campusmap.support.CampusLocationNormalizer;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "campus_building_aliases",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_campus_building_alias_key_campus",
                columnNames = {"alias_key", "campus_code"}
        ),
        indexes = @Index(
                name = "ix_campus_building_alias_building_campus",
                columnList = "building_id,campus_code"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampusBuildingAlias extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", nullable = false, updatable = false)
    private Long buildingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumns(
            value = {
                    @JoinColumn(
                            name = "building_id",
                            referencedColumnName = "id",
                            insertable = false,
                            updatable = false
                    ),
                    @JoinColumn(
                            name = "campus_code",
                            referencedColumnName = "campus_code",
                            insertable = false,
                            updatable = false
                    )
            },
            foreignKey = @ForeignKey(
                    name = "fk_campus_building_alias_building"
            )
    )
    private CampusBuilding building;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(
            name = "campus_code",
            nullable = false,
            length = 20,
            updatable = false
    )
    private CampusCode campusCode;

    @Column(name = "alias_name", nullable = false, length = 255)
    private String aliasName;

    @Column(name = "alias_key", nullable = false, length = 255)
    private String aliasKey;

    private CampusBuildingAlias(CampusBuilding building, String aliasName) {
        this.building = Objects.requireNonNull(
                building,
                "building must not be null"
        );
        this.buildingId = Objects.requireNonNull(
                building.getId(),
                "building must be persisted before creating aliases"
        );
        this.campusCode = Objects.requireNonNull(
                building.getCampusCode(),
                "building.campusCode must not be null"
        );
        if (aliasName == null || aliasName.isBlank()) {
            throw new IllegalArgumentException("aliasName must not be blank");
        }
        this.aliasName = aliasName.trim();
        this.aliasKey = CampusLocationNormalizer.normalize(aliasName);
        if (this.aliasKey.isEmpty()) {
            throw new IllegalArgumentException(
                    "aliasName must contain a searchable character"
            );
        }
    }

    public static CampusBuildingAlias create(
            CampusBuilding building,
            String aliasName
    ) {
        return new CampusBuildingAlias(building, aliasName);
    }

    @PrePersist
    @PreUpdate
    private void validateCampusConsistency() {
        if (building == null
                || !Objects.equals(buildingId, building.getId())
                || campusCode != building.getCampusCode()) {
            throw new IllegalStateException(
                    "alias buildingId and campusCode must match the building"
            );
        }
    }
}
