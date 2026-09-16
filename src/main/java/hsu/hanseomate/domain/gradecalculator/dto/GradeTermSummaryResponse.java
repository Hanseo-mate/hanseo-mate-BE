package hsu.hanseomate.domain.gradecalculator.dto;

import hsu.hanseomate.domain.timetable.composition.entity.Timetable;

public record GradeTermSummaryResponse(
        Long timetableId,
        int year,
        int semester,
        int courseCount,
        GradeSummaryResponse summary
) {
    public static GradeTermSummaryResponse from(
            Timetable timetable,
            int courseCount,
            GradeSummaryResponse summary
    ) {
        return new GradeTermSummaryResponse(
                timetable.getId(),
                timetable.getAcademicYear(),
                timetable.getSemester(),
                courseCount,
                summary
        );
    }
}
