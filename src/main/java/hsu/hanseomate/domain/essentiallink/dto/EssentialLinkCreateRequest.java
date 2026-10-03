package hsu.hanseomate.domain.essentiallink.dto;

import hsu.hanseomate.global.validation.HttpUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EssentialLinkCreateRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "URL은 필수입니다.")
        @Size(max = 2048, message = "URL은 2048자 이하여야 합니다.")
        @HttpUrl
        String url
) {
}
