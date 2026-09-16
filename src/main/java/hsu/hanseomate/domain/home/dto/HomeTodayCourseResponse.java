package hsu.hanseomate.domain.home.dto;

public record HomeTodayCourseResponse(
        String startTime,
        String endTime,
        String courseName,
        String buildingName,
        String roomNumber
) {
}
