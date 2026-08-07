package hsu.hanseomate.domain.timetable.composition.dto;

import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import java.util.List;

public record TimetableDetailResponse(
        Long timetableId,
        int year,
        int semester,
        List<TimetableCourseResponse> courses
) {
    public static TimetableDetailResponse from(
            Timetable timetable,
            List<TimetableCourseResponse> courses
    ) {
        return new TimetableDetailResponse(
                timetable.getId(),
                timetable.getAcademicYear(),
                timetable.getSemester(),
                courses
        );
    }
}
