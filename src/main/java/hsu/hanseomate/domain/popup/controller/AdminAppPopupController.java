package hsu.hanseomate.domain.popup.controller;

import hsu.hanseomate.domain.popup.dto.AppPopupCreateRequest;
import hsu.hanseomate.domain.popup.dto.AppPopupEnabledUpdateRequest;
import hsu.hanseomate.domain.popup.dto.AppPopupResponse;
import hsu.hanseomate.domain.popup.dto.AppPopupUpdateRequest;
import hsu.hanseomate.domain.popup.service.AppPopupService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(
        name = "관리자 앱 시작 팝업 관리",
        description = "ADMIN 권한으로 앱 시작 팝업을 조회, 등록, 수정, 활성화 및 삭제합니다."
)
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/popups")
public class AdminAppPopupController {

    private final AppPopupService appPopupService;

    @Operation(
            summary = "관리자용 팝업 전체 조회",
            description = "노출 중, 예정, 종료 및 비활성 팝업을 최신 등록순으로 모두 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping
    public List<AppPopupResponse> getPopups() {
        return appPopupService.getAdminPopups();
    }

    @Operation(summary = "관리자용 팝업 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "팝업 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/{popupId}")
    public AppPopupResponse getPopup(
            @Positive(message = "팝업 ID는 1 이상이어야 합니다.")
            @PathVariable Long popupId
    ) {
        return appPopupService.getAdminPopup(popupId);
    }

    @Operation(
            summary = "팝업 등록",
            description = "request JSON과 선택 이미지 한 장으로 팝업을 등록합니다. 이미지는 없어도 됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값 또는 이미지",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AppPopupResponse> createPopup(
            @Valid @RequestPart("request") AppPopupCreateRequest request,
            @Parameter(
                    description = "선택 팝업 이미지",
                    schema = @Schema(type = "string", format = "binary")
            )
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        AppPopupResponse response = appPopupService.createPopup(request, image);
        return ResponseEntity.created(
                URI.create("/api/admin/popups/" + response.id())
        ).body(response);
    }

    @Operation(
            summary = "팝업 전체 수정",
            description = "모든 필드를 수정합니다. imageAction으로 기존 이미지 유지, 교체 또는 제거를 지정합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값, ID 또는 이미지",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "팝업 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping(value = "/{popupId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AppPopupResponse updatePopup(
            @Positive(message = "팝업 ID는 1 이상이어야 합니다.")
            @PathVariable Long popupId,
            @Valid @RequestPart("request") AppPopupUpdateRequest request,
            @Parameter(
                    description = "imageAction이 REPLACE일 때 필수인 새 이미지",
                    schema = @Schema(type = "string", format = "binary")
            )
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return appPopupService.updatePopup(popupId, request, image);
    }

    @Operation(summary = "팝업 노출 활성화 상태 변경")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "팝업 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PatchMapping("/{popupId}/enabled")
    public AppPopupResponse updateEnabled(
            @Positive(message = "팝업 ID는 1 이상이어야 합니다.")
            @PathVariable Long popupId,
            @Valid @RequestBody AppPopupEnabledUpdateRequest request
    ) {
        return appPopupService.updateEnabled(popupId, request.enabled());
    }

    @Operation(
            summary = "팝업 삭제",
            description = "팝업 데이터와 서버가 관리하는 이미지를 함께 삭제합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "팝업 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @DeleteMapping("/{popupId}")
    public ResponseEntity<Void> deletePopup(
            @Positive(message = "팝업 ID는 1 이상이어야 합니다.")
            @PathVariable Long popupId
    ) {
        appPopupService.deletePopup(popupId);
        return ResponseEntity.noContent().build();
    }
}
