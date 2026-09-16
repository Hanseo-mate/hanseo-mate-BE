package hsu.hanseomate.domain.courseimport.dto;

import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.ParseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TimetableParseResultRequest(
        @NotBlank @Size(max = 20) String schemaVersion,
        @NotBlank @Size(max = 100) String importId,
        @NotBlank @Size(max = 500) String fileName,
        @NotBlank @Size(min = 64, max = 64) String fileSha256,
        @NotNull CurriculumType curriculumType,
        @NotBlank @Size(max = 100) String parserVersion,
        @Min(value = 2000, message = "academicYear는 2000 이상이어야 합니다.")
        @Max(value = 2100, message = "academicYear는 2100 이하여야 합니다.")
        int academicYear,
        @Min(value = 1, message = "semester는 1 이상이어야 합니다.")
        @Max(value = 2, message = "semester는 2 이하여야 합니다.")
        int semester,
        @NotBlank @Size(max = 255) String displayName,
        @NotNull ParseStatus status,
        @DecimalMin(value = "0.0", message = "confidence는 0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "confidence는 1 이하여야 합니다.")
        double confidence,
        @NotNull @Valid ParseStatisticsRequest statistics,
        @NotNull List<@NotNull @Valid AcademicUnitRequest> academicUnits,
        @NotNull List<@NotNull @Valid GeneralEducationContextRequest> generalCategories,
        @NotNull List<@NotNull @Valid GeneralCategoryNodeRequest> generalCategoryNodes,
        @NotNull List<@NotNull @Valid LectureRequest> lectures,
        @NotNull List<@NotNull @Valid ParseIssueRequest> issues
) {
}
