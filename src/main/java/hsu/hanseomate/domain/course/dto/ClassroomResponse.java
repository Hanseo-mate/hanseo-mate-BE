package hsu.hanseomate.domain.course.dto;

import hsu.hanseomate.domain.course.entity.Classroom;

public record ClassroomResponse(
        String campusCode,
        String buildingName,
        String roomNumber,
        String originalValue
) {
    public static ClassroomResponse from(Classroom classroom) {
        if (classroom == null) {
            return null;
        }
        return new ClassroomResponse(
                classroom.getCampusCode(),
                classroom.getBuildingName(),
                classroom.getRoomNumber(),
                classroom.getOriginalValue()
        );
    }
}
