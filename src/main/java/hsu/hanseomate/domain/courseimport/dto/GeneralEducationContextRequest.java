package hsu.hanseomate.domain.courseimport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record GeneralEducationContextRequest(
        @NotNull GeneralClassification classification,
        @Size(max = 255) String classificationName,
        @Size(max = 100) String categoryCode,
        @Size(max = 255) String categoryName,
        GeneralArea area,
        @NotNull DeliveryProvider deliveryProvider,
        @Size(max = 255) String deliveryProviderName,
        @NotNull List<@NotNull String> sourcePath
) {
}
