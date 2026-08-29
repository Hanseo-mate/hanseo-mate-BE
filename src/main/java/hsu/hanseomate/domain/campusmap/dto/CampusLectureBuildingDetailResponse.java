package hsu.hanseomate.domain.campusmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CampusLectureBuildingDetailResponse(
        @Schema(nullable = true)
        String location,
        @Schema(nullable = true)
        Integer floorCount,
        @Schema(nullable = true)
        Boolean hasElevator,
        @Schema(nullable = true)
        String operatingHours,
        List<String> departments,
        List<String> majorFacilities
) {
    public CampusLectureBuildingDetailResponse {
        departments = List.copyOf(departments);
        majorFacilities = List.copyOf(majorFacilities);
    }
}
