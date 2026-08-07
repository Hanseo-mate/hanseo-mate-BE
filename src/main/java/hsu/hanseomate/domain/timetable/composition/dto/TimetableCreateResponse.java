package hsu.hanseomate.domain.timetable.composition.dto;

import hsu.hanseomate.domain.timetable.composition.entity.Timetable;

public record TimetableCreateResponse(
        Long timetableId,
        int year,
        int semester
) {
    public static TimetableCreateResponse from(Timetable timetable) {
        return new TimetableCreateResponse(
                timetable.getId(),
                timetable.getAcademicYear(),
                timetable.getSemester()
        );
    }
}
