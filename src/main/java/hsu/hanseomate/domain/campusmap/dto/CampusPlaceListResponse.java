package hsu.hanseomate.domain.campusmap.dto;

import java.util.List;

public record CampusPlaceListResponse(
        List<CampusPlaceSummaryResponse> places
) {
    public CampusPlaceListResponse {
        places = List.copyOf(places);
    }
}
