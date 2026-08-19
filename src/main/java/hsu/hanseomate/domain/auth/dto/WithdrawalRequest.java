package hsu.hanseomate.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawalRequest(
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
