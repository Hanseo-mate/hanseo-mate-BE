package hsu.hanseomate.domain.club.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ClubOrderUpdateRequest(
        @Schema(description = "전체 동아리 ID를 표시할 순서대로 전달합니다.", example = "[3, 1, 2]")
        @NotNull(message = "동아리 순서 목록은 필수입니다.")
        List<@NotNull @Positive Long> clubIds
) {
}
