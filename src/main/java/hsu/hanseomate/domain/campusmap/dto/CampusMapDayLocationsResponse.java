package hsu.hanseomate.domain.campusmap.dto;

import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import java.util.List;
import java.util.Objects;

public record CampusMapDayLocationsResponse(
        DayOfWeek dayOfWeek,
        List<CampusMapCourseLocationResponse> courseLocations
) {
    public CampusMapDayLocationsResponse {
        Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
        courseLocations = List.copyOf(courseLocations);
    }
}
