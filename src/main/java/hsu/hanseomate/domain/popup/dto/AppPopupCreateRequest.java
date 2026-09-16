package hsu.hanseomate.domain.popup.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public final class AppPopupCreateRequest {

    @Schema(description = "팝업 제목", maxLength = 200)
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
    private String title;

    @Schema(description = "팝업 본문", maxLength = 100000)
    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 100000, message = "내용은 100,000자 이하여야 합니다.")
    private String content;

    @Schema(
            description = "팝업 이미지 클릭 시 이동할 선택 HTTPS 절대 URL",
            nullable = true,
            maxLength = 2048
    )
    @Size(max = 2048, message = "링크 URL은 2048자 이하여야 합니다.")
    private String linkUrl;

    @Schema(
            description = "의미 기반 클릭 이동 정보. 이동이 없으면 명시적으로 null",
            nullable = true,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @Setter(AccessLevel.NONE)
    private PopupNavigationRequest navigation;

    @Schema(description = "관리자 노출 활성화 여부")
    @NotNull(message = "활성화 여부는 필수입니다.")
    private Boolean enabled;

    @Schema(description = "노출 시작 시각, 미입력 시 즉시", nullable = true)
    private LocalDateTime startsAt;

    @Schema(description = "노출 종료 시각, 미입력 시 무기한", nullable = true)
    private LocalDateTime endsAt;

    @Schema(description = "노출 순서, 작은 값부터 먼저 표시", minimum = "0", maximum = "9999")
    @NotNull(message = "노출 순서는 필수입니다.")
    @Min(value = 0, message = "노출 순서는 0 이상이어야 합니다.")
    @Max(value = 9999, message = "노출 순서는 9999 이하여야 합니다.")
    private Integer displayOrder;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean navigationProvided;

    @JsonSetter("navigation")
    public void setNavigation(PopupNavigationRequest navigation) {
        this.navigation = navigation;
        this.navigationProvided = true;
    }

    @JsonIgnore
    public boolean hasNavigation() {
        return navigationProvided;
    }

}
