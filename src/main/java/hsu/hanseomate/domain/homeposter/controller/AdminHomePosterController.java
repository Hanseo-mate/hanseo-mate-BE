package hsu.hanseomate.domain.homeposter.controller;

import hsu.hanseomate.domain.homeposter.dto.HomePosterResponse;
import hsu.hanseomate.domain.homeposter.service.HomePosterService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(
        name = "관리자 홈 포스터 관리",
        description = "ADMIN 권한으로 홈 포스터를 등록, 조회, 교체, 삭제합니다."
)
@Validated
@RestController
@RequestMapping("/api/admin/home-posters")
@RequiredArgsConstructor
public class AdminHomePosterController {

    private final HomePosterService homePosterService;

    @Operation(
            summary = "홈 포스터 등록",
            description = "이미지 한 장을 새 포스터로 등록합니다. 등록 가능한 포스터 수에는 제한이 없습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "파일 누락 또는 잘못된 이미지",
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
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "서버 multipart 업로드 허용 크기 초과",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HomePosterResponse> createPoster(
            @RequestPart("file") MultipartFile file
    ) {
        HomePosterResponse response = homePosterService.createPoster(file);
        return ResponseEntity.created(
                URI.create("/api/admin/home-posters/" + response.id())
        ).body(response);
    }

    @Operation(
            summary = "홈 포스터 목록 조회",
            description = "등록된 모든 포스터를 등록 순서대로 조회합니다. 포스터가 없으면 빈 배열을 반환합니다."
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
    public List<HomePosterResponse> getPosters() {
        return homePosterService.getPosters();
    }

    @Operation(
            summary = "홈 포스터 이미지 교체",
            description = "포스터 ID는 유지하고 이미지 파일과 URL만 교체합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "교체 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 포스터 ID, 파일 누락 또는 잘못된 이미지",
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
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "포스터 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "서버 multipart 업로드 허용 크기 초과",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping(
            value = "/{posterId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public HomePosterResponse replacePoster(
            @Positive(message = "포스터 ID는 1 이상이어야 합니다.")
            @PathVariable Long posterId,
            @RequestPart("file") MultipartFile file
    ) {
        return homePosterService.replacePoster(posterId, file);
    }

    @Operation(
            summary = "홈 포스터 삭제",
            description = "포스터 데이터와 서버가 관리하는 이미지 파일을 함께 삭제합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 포스터 ID",
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
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "포스터 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @DeleteMapping("/{posterId}")
    public ResponseEntity<Void> deletePoster(
            @Positive(message = "포스터 ID는 1 이상이어야 합니다.")
            @PathVariable Long posterId
    ) {
        homePosterService.deletePoster(posterId);
        return ResponseEntity.noContent().build();
    }
}
