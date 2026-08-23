package hsu.hanseomate.domain.gradecalculator.dto;

import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import java.util.List;

public record TimetableGradeCoursesResponse(
        Long timetableId,
        int year,
        int semester,
        List<TimetableGradeCourseResponse> courses
) {
    public static TimetableGradeCoursesResponse from(
            Timetable timetable,
            List<TimetableGradeCourseResponse> courses
    ) {
        return new TimetableGradeCoursesResponse(
                timetable.getId(),
                timetable.getAcademicYear(),
                timetable.getSemester(),
                List.copyOf(courses)
        );
    }
}
