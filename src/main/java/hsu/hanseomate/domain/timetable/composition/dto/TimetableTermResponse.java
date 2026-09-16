package hsu.hanseomate.domain.timetable.composition.dto;

import hsu.hanseomate.domain.timetable.composition.entity.Timetable;

public record TimetableTermResponse(
        Long timetableId,
        int year,
        int semester
) {
    public static TimetableTermResponse from(Timetable timetable) {
        return new TimetableTermResponse(
                timetable.getId(),
                timetable.getAcademicYear(),
                timetable.getSemester()
        );
    }
}
