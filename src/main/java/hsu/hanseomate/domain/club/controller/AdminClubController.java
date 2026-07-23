package hsu.hanseomate.domain.club.controller;

import hsu.hanseomate.domain.club.dto.ClubBasicInfoUpdateRequest;
import hsu.hanseomate.domain.club.dto.ClubCreateRequest;
import hsu.hanseomate.domain.club.dto.ClubCreateResponse;
import hsu.hanseomate.domain.club.dto.ClubImageUploadResponse;
import hsu.hanseomate.domain.club.dto.ClubInformationUpdateRequest;
import hsu.hanseomate.domain.club.dto.ClubRecruitmentUpdateRequest;
import hsu.hanseomate.domain.club.service.ClubService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(
        name = "관리자 동아리 관리",
        description = "동아리 기본 정보, 장문 소개, 활동 내용과 모집공고를 관리합니다. 현재는 인증을 적용하지 않습니다."
)
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/clubs")
public class AdminClubController {

    private final ClubService clubService;

    @Operation(
            summary = "동아리 등록",
            description = "동아리 이름과 분과만 먼저 등록합니다. 나머지 정보와 이미지는 등록 후 별도 API로 수정합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값 또는 중복된 동아리명",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<ClubCreateResponse> createClub(
            @Valid @RequestBody ClubCreateRequest request
    ) {
        ClubCreateResponse response = clubService.createClub(request);
        return ResponseEntity.created(URI.create("/api/clubs/" + response.id())).body(response);
    }

    @Operation(
            summary = "배경 이미지 업로드",
            description = "JPG, PNG 또는 GIF 파일을 업로드하고 접근 가능한 이미지 URL을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 동아리 ID 또는 이미지 파일",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping(
            value = "/background-images/{clubId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ClubImageUploadResponse updateBackgroundImage(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId,
            @RequestPart("file") MultipartFile file
    ) {
        return clubService.updateBackgroundImage(clubId, file);
    }

    @Operation(
            summary = "프로필 이미지 업로드",
            description = "JPG, PNG 또는 GIF 파일을 업로드하고 접근 가능한 이미지 URL을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 동아리 ID 또는 이미지 파일",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping(
            value = "/profile-images/{clubId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ClubImageUploadResponse updateProfileImage(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId,
            @RequestPart("file") MultipartFile file
    ) {
        return clubService.updateProfileImage(clubId, file);
    }

    @Operation(summary = "동아리명과 한 줄 소개 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값 또는 중복된 동아리명",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/basic-info/{clubId}")
    public ResponseEntity<Void> updateBasicInfo(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId,
            @Valid @RequestBody ClubBasicInfoUpdateRequest request
    ) {
        clubService.updateBasicInfo(clubId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "동아리 소개·활동 내용·문의 링크 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 동아리 ID 또는 URL",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/information/{clubId}")
    public ResponseEntity<Void> updateInformation(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId,
            @Valid @RequestBody ClubInformationUpdateRequest request
    ) {
        clubService.updateInformation(clubId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "모집공고 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 동아리 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/recruitments/{clubId}")
    public ResponseEntity<Void> updateRecruitment(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId,
            @Valid @RequestBody ClubRecruitmentUpdateRequest request
    ) {
        clubService.updateRecruitment(clubId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "동아리 삭제", description = "연결된 좋아요와 활동 후기도 함께 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 동아리 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @DeleteMapping("/{clubId}")
    public ResponseEntity<Void> deleteClub(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId
    ) {
        clubService.deleteClub(clubId);
        return ResponseEntity.noContent().build();
    }
}
