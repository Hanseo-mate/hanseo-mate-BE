package hsu.hanseomate.domain.campusmap.dto;

import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.domain.campusmap.type.CampusPlaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;

public record CampusPlaceSummaryResponse(
        Long placeId,
        CampusCode campusCode,
        String placeName,
        @Schema(nullable = true)
        CampusPlaceCategory category,
        @Schema(nullable = true)
        String categoryName,
        @Schema(nullable = true)
        String oneLineDescription,
        @Schema(nullable = true, description = "장소 주소. 교내시설은 위치 설명(location)과 동일")
        String address,
        @Schema(nullable = true, format = "uri")
        String imageUrl,
        double latitude,
        double longitude
) {
}
