package hsu.hanseomate.domain.timetable.search.dto;

import hsu.hanseomate.domain.course.entity.AcademicUnit;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.entity.OfferingGeneralEducation;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
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
        GeneralCategoryFilter generalCategory,
        List<CourseSearchScheduleResponse> schedules
) {
    private static final List<String> MAJOR_CYBER_MARKERS = List.of(
            "온라인",
            "사이버",
            "원격"
    );

    public static CourseOfferingResponse from(
            CourseOffering offering,
            List<CourseSchedule> schedules
    ) {
        return new CourseOfferingResponse(
                offering.getId(),
                offering.getCourseName(),
                offering.getSectionNo(),
                displayCredit(offering.getCredit()),
                isCyber(offering),
                offering.getInstructorName(),
                offering.getCurriculumType(),
                offering.getTargetGrade(),
                originalAcademicUnitName(offering.getAcademicUnit()),
                generalCategory(offering.getGeneralEducation()),
                schedules.stream().map(CourseSearchScheduleResponse::from).toList()
        );
    }

    static BigDecimal displayCredit(BigDecimal credit) {
        return credit == null ? null : credit.stripTrailingZeros();
    }

    static boolean isCyber(CourseOffering offering) {
        if (offering.getCurriculumType() == CurriculumType.GENERAL_EDUCATION) {
            OfferingGeneralEducation generalEducation = offering.getGeneralEducation();
            return generalEducation != null
                    && isCyberProvider(generalEducation.getDeliveryProvider());
        }
        return containsMajorCyberMarker(offering.getNote());
    }

    static boolean containsMajorCyberMarker(String note) {
        if (note == null || note.isBlank()) {
            return false;
        }
        return MAJOR_CYBER_MARKERS.stream().anyMatch(note::contains);
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

    static GeneralCategoryFilter generalCategory(OfferingGeneralEducation generalEducation) {
        if (generalEducation == null) {
            return null;
        }
        return generalCategory(
                generalEducation.getClassification(),
                generalEducation.getArea(),
                generalEducation.getDeliveryProvider()
        );
    }

    static GeneralCategoryFilter generalCategory(
            GeneralClassification classification,
            GeneralArea area,
            DeliveryProvider provider
    ) {
        if (classification == GeneralClassification.REQUIRED) {
            return GeneralCategoryFilter.REQUIRED;
        }
        GeneralCategoryFilter remoteCategory = remoteCategory(provider);
        if (remoteCategory != null) {
            return remoteCategory;
        }
        if (area == null) {
            return GeneralCategoryFilter.OTHER;
        }
        return switch (area) {
            case EXPLORATION -> GeneralCategoryFilter.AREA_1;
            case COEXISTENCE -> GeneralCategoryFilter.AREA_2;
            case INITIATIVE -> GeneralCategoryFilter.AREA_3;
            case OTHER -> GeneralCategoryFilter.OTHER;
        };
    }

    private static GeneralCategoryFilter remoteCategory(DeliveryProvider provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider) {
            case E_CLASS -> GeneralCategoryFilter.E_CLASS;
            case HSU_CYBER -> GeneralCategoryFilter.HSU_CYBER;
            case OCU -> GeneralCategoryFilter.OCU;
            case CHUNGNAM_ELEARNING -> GeneralCategoryFilter.CHUNGNAM_ELEARNING;
            case SDU -> GeneralCategoryFilter.SDU;
            case ON_CAMPUS, OTHER -> null;
        };
    }

    private static String originalAcademicUnitName(AcademicUnit academicUnit) {
        return academicUnit == null ? null : academicUnit.getOriginalName();
    }
}
