package hsu.hanseomate.domain.course.support;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.OfferingGeneralEducation;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import java.util.List;

public final class CourseCyberPolicy {

    private static final List<String> MAJOR_CYBER_MARKERS = List.of(
            "온라인",
            "사이버",
            "원격"
    );

    private CourseCyberPolicy() {
    }

    public static boolean isCyber(CourseOffering offering) {
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
}
