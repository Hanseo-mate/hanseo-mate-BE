package hsu.hanseomate.domain.timetable.search.dto;

import tools.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
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
        boolean cyber,
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
                isCyber(offering),
                AcademicUnitResponse.from(offering.getAcademicUnit()),
                GeneralEducationResponse.from(offering.getGeneralEducation(), objectMapper),
                offering.getTargetGrade(),
                offering.isCommonGrade(),
                schedules.stream().map(CourseScheduleResponse::from).toList()
        );
    }

    private static boolean isCyber(CourseOffering offering) {
        if (offering.getCurriculumType() != CurriculumType.GENERAL_EDUCATION
                || offering.getGeneralEducation() == null) {
            return false;
        }
        return isCyberProvider(offering.getGeneralEducation().getDeliveryProvider());
    }

    static boolean isCyberProvider(DeliveryProvider provider) {
        if (provider == null) {
            return false;
        }
        return switch (provider) {
            case HSU_CYBER, OCU, CHUNGNAM_ELEARNING, SDU -> true;
            case ON_CAMPUS, E_CLASS, OTHER -> false;
        };
    }
}
