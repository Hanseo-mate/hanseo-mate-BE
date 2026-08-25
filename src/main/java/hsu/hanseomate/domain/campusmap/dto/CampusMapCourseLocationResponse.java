package hsu.hanseomate.domain.campusmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CampusMapCourseLocationResponse(
        UUID scheduleId,
        @Schema(nullable = true)
        String courseName,
        List<Integer> periods,
        @Schema(nullable = true)
        String campusCode,
        @Schema(nullable = true)
        String buildingName,
        @Schema(nullable = true)
        String roomNumber,
        @Schema(nullable = true)
        String canonicalBuildingName,
        @Schema(nullable = true)
        Double latitude,
        @Schema(nullable = true)
        Double longitude,
        CampusMapLocationStatus locationStatus
) {
    public CampusMapCourseLocationResponse {
        Objects.requireNonNull(scheduleId, "scheduleId must not be null");
        periods = List.copyOf(periods);
        Objects.requireNonNull(locationStatus, "locationStatus must not be null");

        boolean bothCoordinatesExist = latitude != null && longitude != null;
        boolean bothCoordinatesAreMissing = latitude == null && longitude == null;
        if (!bothCoordinatesExist && !bothCoordinatesAreMissing) {
            throw new IllegalArgumentException(
                    "latitude and longitude must both exist or both be null"
            );
        }
        if ((locationStatus == CampusMapLocationStatus.MAPPED)
                != bothCoordinatesExist) {
            throw new IllegalArgumentException(
                    "only MAPPED locations may contain coordinates"
            );
        }
    }
}
