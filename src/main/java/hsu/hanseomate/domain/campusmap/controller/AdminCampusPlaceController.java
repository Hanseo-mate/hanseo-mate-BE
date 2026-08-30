package hsu.hanseomate.domain.campusmap.controller;

import hsu.hanseomate.domain.campusmap.dto.CampusPlaceDetailResponse;
import hsu.hanseomate.domain.campusmap.dto.CampusPlaceInformationUpdateRequest;
import hsu.hanseomate.domain.campusmap.service.CampusPlaceService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "관리자 캠퍼스 장소",
        description = "기존 캠퍼스 장소의 표시 정보와 카테고리 상세 정보를 관리합니다."
)
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/campus-map/places")
public class AdminCampusPlaceController {

    private final CampusPlaceService campusPlaceService;

    @Operation(
            summary = "장소 등록",
            description = "장소명으로 내부 키를 생성하고 새 장소와 카테고리 상세정보를 등록합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값 또는 같은 캠퍼스의 중복 장소",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요")
    })
    @PostMapping
    public ResponseEntity<CampusPlaceDetailResponse> createPlace(
            @Valid @RequestBody CampusPlaceInformationUpdateRequest request
    ) {
        CampusPlaceDetailResponse response = campusPlaceService.createPlace(request);
        return ResponseEntity.created(URI.create(
                "/api/campus-map/places/" + response.placeId()
        )).body(response);
    }

    @Operation(
            summary = "장소 전체 수정",
            description = "기존 장소의 이름, 캠퍼스, 좌표, 표시 정보와 교내시설 상세정보를 전체 교체합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값 또는 카테고리 상세정보 불일치",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 토큰이 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/{placeId}")
    public CampusPlaceDetailResponse updatePlaceInformation(
            @Positive(message = "장소 ID는 1 이상이어야 합니다.")
            @PathVariable Long placeId,
            @Valid @RequestBody CampusPlaceInformationUpdateRequest request
    ) {
        return campusPlaceService.updatePlace(placeId, request);
    }

    @Operation(
            summary = "장소 삭제",
            description = "장소와 연결된 교내시설 상세정보를 함께 삭제합니다. 업로드 이미지 파일은 삭제하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요"),
            @ApiResponse(
                    responseCode = "404",
                    description = "장소 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @DeleteMapping("/{placeId}")
    public ResponseEntity<Void> deletePlace(
            @Positive(message = "장소 ID는 1 이상이어야 합니다.")
            @PathVariable Long placeId
    ) {
        campusPlaceService.deletePlace(placeId);
        return ResponseEntity.noContent().build();
    }
}
