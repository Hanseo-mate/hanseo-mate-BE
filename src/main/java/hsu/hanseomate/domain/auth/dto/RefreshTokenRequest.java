package hsu.hanseomate.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken은 필수입니다.")
        @Size(max = 512, message = "refreshToken은 512자 이하여야 합니다.")
        String refreshToken
) {
}
