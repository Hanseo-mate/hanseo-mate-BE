package hsu.hanseomate.domain.campusmap.controller;

import hsu.hanseomate.domain.campusmap.dto.CampusPlaceImageUploadResponse;
import hsu.hanseomate.domain.campusmap.service.CampusPlaceImageService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(
        name = "관리자 캠퍼스 장소",
        description = "캠퍼스 장소 대표 이미지를 업로드합니다."
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/campus-map/place-images")
public class AdminCampusPlaceImageController {

    private final CampusPlaceImageService campusPlaceImageService;

    @Operation(
            summary = "장소 이미지 업로드",
            description = "이미지를 저장하고 URL만 반환합니다. 장소 DB 행은 수정하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "비어 있거나 지원하지 않는 이미지",
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
            )
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CampusPlaceImageUploadResponse upload(
            @RequestPart("file") MultipartFile file
    ) {
        return campusPlaceImageService.upload(file);
    }
}
