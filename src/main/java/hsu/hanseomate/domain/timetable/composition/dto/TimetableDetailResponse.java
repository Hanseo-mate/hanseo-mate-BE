package hsu.hanseomate.domain.timetable.composition.dto;

import hsu.hanseomate.domain.gradecalculator.dto.GradeCompactSummaryResponse;
import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import java.util.List;

public record TimetableDetailResponse(
        Long timetableId,
        int year,
        int semester,
        List<TimetableCourseResponse> courses,
        List<TimetableCourseResponse> cyberCourses,
        GradeCompactSummaryResponse gradeSummary
) {
    public static TimetableDetailResponse from(
            Timetable timetable,
            List<TimetableCourseResponse> courses,
            GradeCompactSummaryResponse gradeSummary
    ) {
        List<TimetableCourseResponse> regularCourses = courses.stream()
                .filter(course -> !course.cyber())
                .toList();
        List<TimetableCourseResponse> cyberCourses = courses.stream()
                .filter(TimetableCourseResponse::cyber)
                .toList();
        return new TimetableDetailResponse(
                timetable.getId(),
                timetable.getAcademicYear(),
                timetable.getSemester(),
                regularCourses,
                cyberCourses,
                gradeSummary
        );
    }
}
