package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.domain.club.type.ClubReviewOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClubReviewSaveRequest(
        @Size(max = 5, message = "활동 후기 태그는 최대 5개까지 선택할 수 있습니다.")
        List<@NotNull(message = "활동 후기 태그에는 null을 포함할 수 없습니다.") @Valid ClubReviewOption> reviewTags
) {
}
