package hsu.hanseomate.domain.campusmap.dto;

import hsu.hanseomate.domain.campusmap.type.CampusCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CampusPlaceListResponse(
        @Schema(nullable = true, description = "이번 조회에 적용된 캠퍼스")
        CampusCode selectedCampusCode,
        List<CampusPlaceSummaryResponse> places
) {
    public CampusPlaceListResponse {
        places = List.copyOf(places);
    }
}
