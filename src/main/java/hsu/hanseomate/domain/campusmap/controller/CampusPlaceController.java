package hsu.hanseomate.domain.campusmap.controller;

import hsu.hanseomate.domain.campusmap.dto.CampusPlaceDetailResponse;
import hsu.hanseomate.domain.campusmap.dto.CampusPlaceListResponse;
import hsu.hanseomate.domain.campusmap.service.CampusPlaceService;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.domain.campusmap.type.CampusPlaceCategory;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "캠퍼스 장소",
        description = "지도 마커와 장소의 공통 정보를 조회합니다."
)
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/campus-map/places")
public class CampusPlaceController {

    private final CampusPlaceService campusPlaceService;

    @Operation(
            summary = "캠퍼스 장소 목록 조회",
            description = "campusCode가 없으면 로그인 사용자의 선호 학생식당을 캠퍼스로 변환해 필터링합니다. "
                    + "비로그인은 전체 캠퍼스를 조회하며, 미분류 장소의 category는 null입니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public CampusPlaceListResponse getPlaces(
            @RequestParam(required = false) CampusCode campusCode,
            @RequestParam(required = false) CampusPlaceCategory category,
            Authentication authentication
    ) {
        return campusPlaceService.getPlaces(
                optionalCurrentUserId(authentication),
                campusCode,
                category
        );
    }

    @Operation(
            summary = "캠퍼스 장소 상세 조회",
            description = "장소의 공통 정보와 교내시설 카테고리의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/{placeId}")
    public CampusPlaceDetailResponse getPlace(
            @Positive(message = "장소 ID는 1 이상이어야 합니다.")
            @PathVariable Long placeId
    ) {
        return campusPlaceService.getPlace(placeId);
    }

    private Optional<Long> optionalCurrentUserId(
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(authentication.getName()));
        } catch (NumberFormatException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "유효하지 않은 인증 정보입니다.",
                    exception
            );
        }
    }
}
