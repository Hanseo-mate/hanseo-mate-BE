package hsu.hanseomate.domain.courseimport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralCategoryNodeType;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record GeneralCategoryNodeRequest(
        @NotBlank @Size(max = 255) String nodeKey,
        @NotNull GeneralCategoryNodeType nodeType,
        String code,
        @NotBlank @Size(max = 500) String name,
        @Size(max = 255) String parentKey,
        GeneralClassification classification,
        String classificationName,
        GeneralArea area,
        DeliveryProvider deliveryProvider,
        String deliveryProviderName,
        @NotNull List<@NotNull String> sourcePath,
        @NotBlank @Size(max = 255) String sourceSheet,
        @Min(value = 1, message = "sourceRow는 1 이상이어야 합니다.") int sourceRow,
        @Min(value = 1, message = "sortOrder는 1 이상이어야 합니다.") int sortOrder
) {
}
