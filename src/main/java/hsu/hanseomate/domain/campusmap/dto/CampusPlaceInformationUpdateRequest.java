package hsu.hanseomate.domain.campusmap.dto;

import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.domain.campusmap.type.CampusPlaceCategory;
import hsu.hanseomate.global.validation.HttpUrl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CampusPlaceInformationUpdateRequest(
        @NotNull(message = "캠퍼스 코드는 필수입니다.")
        CampusCode campusCode,

        @NotBlank(message = "장소명은 필수입니다.")
        @Size(max = 255, message = "장소명은 255자 이하여야 합니다.")
        String placeName,

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.")
        @Digits(integer = 3, fraction = 9, message = "위도는 소수점 이하 9자리 이하여야 합니다.")
        BigDecimal latitude,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.")
        @Digits(integer = 3, fraction = 9, message = "경도는 소수점 이하 9자리 이하여야 합니다.")
        BigDecimal longitude,

        @NotNull(message = "카테고리는 필수입니다.")
        CampusPlaceCategory category,

        @NotBlank(message = "한 줄 소개는 필수입니다.")
        @Size(max = 255, message = "한 줄 소개는 255자 이하여야 합니다.")
        String oneLineDescription,

        @NotBlank(message = "이미지 URL은 필수입니다.")
        @Size(max = 2048, message = "이미지 URL은 2048자 이하여야 합니다.")
        @HttpUrl
        String imageUrl,

        @Valid
        CampusLectureBuildingDetailUpdateRequest lectureBuildingDetails
) {
}
