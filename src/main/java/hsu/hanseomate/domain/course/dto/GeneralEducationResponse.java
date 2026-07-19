package hsu.hanseomate.domain.course.dto;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.course.entity.OfferingGeneralEducation;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import java.util.List;

public record GeneralEducationResponse(
        GeneralClassification classification,
        String classificationName,
        String categoryCode,
        String categoryName,
        GeneralArea area,
        DeliveryProvider deliveryProvider,
        String deliveryProviderName,
        List<String> sourcePath
) {
    public static GeneralEducationResponse from(
            OfferingGeneralEducation context,
            ObjectMapper objectMapper
    ) {
        if (context == null) {
            return null;
        }
        return new GeneralEducationResponse(
                context.getClassification(),
                context.getClassificationName(),
                context.getCategoryCode(),
                context.getCategoryName(),
                context.getArea(),
                context.getDeliveryProvider(),
                context.getDeliveryProviderName(),
                parsePath(context.getSourcePathJson(), objectMapper)
        );
    }

    private static List<String> parsePath(String value, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("저장된 교양 분류 경로를 읽을 수 없습니다.", exception);
        }
    }
}
