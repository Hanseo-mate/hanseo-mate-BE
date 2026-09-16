package hsu.hanseomate.domain.timetable.search.support;

import hsu.hanseomate.domain.course.entity.OfferingGeneralEducation;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import hsu.hanseomate.domain.timetable.search.type.GeneralCategoryFilter;

public final class GeneralCategoryResolver {

    private GeneralCategoryResolver() {
    }

    public static GeneralCategoryFilter resolve(OfferingGeneralEducation generalEducation) {
        if (generalEducation == null) {
            return null;
        }
        return resolve(
                generalEducation.getClassification(),
                generalEducation.getArea(),
                generalEducation.getDeliveryProvider()
        );
    }

    public static GeneralCategoryFilter resolve(
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
}
