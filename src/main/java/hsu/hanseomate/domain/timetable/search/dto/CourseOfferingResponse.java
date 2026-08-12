package hsu.hanseomate.domain.timetable.search.dto;

import hsu.hanseomate.domain.course.entity.AcademicUnit;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.support.CourseCyberPolicy;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import hsu.hanseomate.domain.timetable.search.support.GeneralCategoryResolver;
import hsu.hanseomate.domain.timetable.search.type.GeneralCategoryFilter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CourseOfferingResponse(
        UUID offeringId,
        String courseName,
        String sectionNo,
        BigDecimal credit,
        boolean cyber,
        String instructorName,
        CurriculumType curriculumType,
        Integer targetGrade,
        String originalAcademicUnitName,
        List<String> eligibleDepartmentNames,
        GeneralCategoryFilter generalCategory,
        List<CourseSearchScheduleResponse> schedules
) {
    public static CourseOfferingResponse from(
            CourseOffering offering,
            List<CourseSchedule> schedules,
            List<String> eligibleDepartmentNames
    ) {
        return new CourseOfferingResponse(
                offering.getId(),
                offering.getCourseName(),
                offering.getSectionNo(),
                displayCredit(offering.getCredit()),
                CourseCyberPolicy.isCyber(offering),
                offering.getInstructorName(),
                offering.getCurriculumType(),
                offering.getTargetGrade(),
                originalAcademicUnitName(offering.getAcademicUnit()),
                List.copyOf(eligibleDepartmentNames),
                GeneralCategoryResolver.resolve(offering.getGeneralEducation()),
                schedules.stream().map(CourseSearchScheduleResponse::from).toList()
        );
    }

    static BigDecimal displayCredit(BigDecimal credit) {
        return credit == null ? null : credit.stripTrailingZeros();
    }

    static GeneralCategoryFilter generalCategory(
            GeneralClassification classification,
            GeneralArea area,
            DeliveryProvider provider
    ) {
        return GeneralCategoryResolver.resolve(classification, area, provider);
    }

    private static String originalAcademicUnitName(AcademicUnit academicUnit) {
        return academicUnit == null ? null : academicUnit.getOriginalName();
    }
}
