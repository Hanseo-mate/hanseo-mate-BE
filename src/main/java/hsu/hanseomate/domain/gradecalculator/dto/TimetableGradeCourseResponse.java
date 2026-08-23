package hsu.hanseomate.domain.gradecalculator.dto;

import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.gradecalculator.type.ExpectedGrade;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import java.math.BigDecimal;
import java.util.UUID;

public record TimetableGradeCourseResponse(
        Long timetableCourseId,
        UUID courseId,
        String courseName,
        BigDecimal credit,
        CurriculumType curriculumType,
        ExpectedGrade expectedGrade
) {
    public static TimetableGradeCourseResponse from(TimetableCourse timetableCourse) {
        var offering = timetableCourse.getCourseOffering();
        return new TimetableGradeCourseResponse(
                timetableCourse.getId(),
                offering.getId(),
                timetableCourse.getGradeCourseName(),
                timetableCourse.getGradeCredit(),
                offering.getScopeCurriculumType(),
                timetableCourse.getExpectedGrade()
        );
    }
}
