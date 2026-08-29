package hsu.hanseomate.domain.campusmap.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "campus_lecture_building_details")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampusLectureBuildingDetail extends BaseTimeEntity {

    @Id
    @Column(name = "place_id")
    private Long placeId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "place_id",
            foreignKey = @ForeignKey(
                    name = "fk_campus_lecture_building_detail_place"
            )
    )
    private CampusPlace place;

    @Column(name = "location_description", length = 255)
    private String location;

    @Column(name = "floor_count")
    private Integer floorCount;

    @Column(name = "has_elevator")
    private Boolean hasElevator;

    @Column(name = "operating_hours", length = 255)
    private String operatingHours;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "campus_lecture_building_departments",
            joinColumns = @JoinColumn(name = "place_id"),
            foreignKey = @ForeignKey(
                    name = "fk_campus_lecture_building_department_place"
            )
    )
    @OrderColumn(name = "sort_order")
    @Column(name = "department_name", nullable = false, length = 255)
    private List<String> departments = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "campus_lecture_building_facilities",
            joinColumns = @JoinColumn(name = "place_id"),
            foreignKey = @ForeignKey(
                    name = "fk_campus_lecture_building_facility_place"
            )
    )
    @OrderColumn(name = "sort_order")
    @Column(name = "facility_name", nullable = false, length = 255)
    private List<String> majorFacilities = new ArrayList<>();

    public static CampusLectureBuildingDetail create(CampusPlace place) {
        CampusLectureBuildingDetail detail = new CampusLectureBuildingDetail();
        detail.place = place;
        return detail;
    }

    public void update(
            String location,
            Integer floorCount,
            Boolean hasElevator,
            String operatingHours,
            List<String> departments,
            List<String> majorFacilities
    ) {
        this.location = location;
        this.floorCount = floorCount;
        this.hasElevator = hasElevator;
        this.operatingHours = operatingHours;
        this.departments.clear();
        this.departments.addAll(departments);
        this.majorFacilities.clear();
        this.majorFacilities.addAll(majorFacilities);
    }
}
