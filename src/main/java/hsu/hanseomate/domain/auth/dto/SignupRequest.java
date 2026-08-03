package hsu.hanseomate.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(max = 100, message = "아이디는 100자 이하여야 합니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
