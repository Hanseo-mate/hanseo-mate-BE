package hsu.hanseomate.domain.campusmap.dto;

import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record CampusMapTodayResponse(
        LocalDate date,
        DayOfWeek dayOfWeek,
        int academicYear,
        int semester,
        List<CampusMapCourseLocationResponse> courseLocations
) {
    public CampusMapTodayResponse {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
        courseLocations = List.copyOf(courseLocations);
    }
}
