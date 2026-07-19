package hsu.hanseomate.domain.courseimport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record GeneralEducationContextRequest(
        @NotNull GeneralClassification classification,
        String classificationName,
        String categoryCode,
        String categoryName,
        GeneralArea area,
        @NotNull DeliveryProvider deliveryProvider,
        String deliveryProviderName,
        @NotNull List<@NotNull String> sourcePath
) {
}
