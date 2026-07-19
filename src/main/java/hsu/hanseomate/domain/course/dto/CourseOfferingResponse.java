package hsu.hanseomate.domain.course.dto;

import tools.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CourseOfferingResponse(
        UUID offeringId,
        String courseCode,
        String courseName,
        String sectionNo,
        BigDecimal credit,
        String instructorName,
        CurriculumType curriculumType,
        AcademicUnitResponse academicUnit,
        GeneralEducationResponse generalEducation,
        Integer targetGrade,
        boolean commonGrade,
        List<CourseScheduleResponse> schedules
) {
    public static CourseOfferingResponse from(
            CourseOffering offering,
            List<CourseSchedule> schedules,
            ObjectMapper objectMapper
    ) {
        return new CourseOfferingResponse(
                offering.getId(),
                offering.getCourseCode(),
                offering.getCourseName(),
                offering.getSectionNo(),
                offering.getCredit(),
                offering.getInstructorName(),
                offering.getCurriculumType(),
                AcademicUnitResponse.from(offering.getAcademicUnit()),
                GeneralEducationResponse.from(offering.getGeneralEducation(), objectMapper),
                offering.getTargetGrade(),
                offering.isCommonGrade(),
                schedules.stream().map(CourseScheduleResponse::from).toList()
        );
    }
}
