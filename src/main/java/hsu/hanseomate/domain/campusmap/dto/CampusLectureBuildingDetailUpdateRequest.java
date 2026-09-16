package hsu.hanseomate.domain.campusmap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CampusLectureBuildingDetailUpdateRequest(
        @NotBlank(message = "위치는 필수입니다.")
        @Size(max = 255, message = "위치는 255자 이하여야 합니다.")
        String location,

        @NotNull(message = "층수는 필수입니다.")
        @Positive(message = "층수는 1 이상이어야 합니다.")
        Integer floorCount,

        @NotNull(message = "엘리베이터 유무는 필수입니다.")
        Boolean hasElevator,

        @NotEmpty(message = "주요시설을 한 개 이상 입력해야 합니다.")
        @Size(max = 50, message = "주요시설은 50개 이하여야 합니다.")
        List<
                @NotBlank(message = "주요시설 이름은 비어 있을 수 없습니다.")
                @Size(max = 255, message = "주요시설 이름은 255자 이하여야 합니다.")
                String
        > majorFacilities
) {
}
