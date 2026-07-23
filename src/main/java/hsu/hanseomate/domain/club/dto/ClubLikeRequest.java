package hsu.hanseomate.domain.club.dto;

import jakarta.validation.constraints.NotNull;

public record ClubLikeRequest(
        @NotNull(message = "좋아요 상태는 필수입니다.")
        Boolean liked
) {
}
