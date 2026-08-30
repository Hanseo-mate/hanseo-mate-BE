package hsu.hanseomate.domain.campusmap.dto;

import java.util.List;

public record CampusMapWeeklyResponse(
        int academicYear,
        int semester,
        List<CampusMapDayLocationsResponse> dayLocations
) {
    public CampusMapWeeklyResponse {
        dayLocations = List.copyOf(dayLocations);
    }
}
