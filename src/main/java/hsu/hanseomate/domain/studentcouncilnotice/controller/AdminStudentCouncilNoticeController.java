package hsu.hanseomate.domain.studentcouncilnotice.controller;

import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeDetailResponse;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeMultipartRequest;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticePageResponse;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeRequest;
import hsu.hanseomate.domain.studentcouncilnotice.service.StudentCouncilNoticeService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(
        name = "관리자 학생회 공지 관리",
        description = "ADMIN 권한으로 학생회 공지를 조회, 등록, 수정, 삭제합니다."
)
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notices")
public class AdminStudentCouncilNoticeController {

    private final StudentCouncilNoticeService studentCouncilNoticeService;

    @Operation(
            summary = "관리자용 학생회 공지 목록 조회",
            description = "학생회 공지와 현재 조회수를 최신 작성순으로 10개씩 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 페이지 번호",
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
    @GetMapping
    public StudentCouncilNoticePageResponse getNotices(
            @Parameter(description = "0부터 시작하는 페이지 번호")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
            int page
    ) {
        return studentCouncilNoticeService.getNotices(page);
    }

    @Operation(
            summary = "관리자용 학생회 공지 상세 조회",
            description = "조회수를 증가시키지 않고 학생회 공지와 현재 조회수를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 공지 ID",
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
                    description = "학생회 공지 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/{noticeId}")
    public StudentCouncilNoticeDetailResponse getNotice(
            @Parameter(description = "학생회 공지 ID", required = true)
            @Positive(message = "공지 ID는 1 이상이어야 합니다.")
            @PathVariable Long noticeId
    ) {
        return studentCouncilNoticeService.getNoticeForAdmin(noticeId);
    }

    @Operation(summary = "학생회 공지 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StudentCouncilNoticeDetailResponse> createNotice(
            @Valid @RequestBody StudentCouncilNoticeRequest request
    ) {
        StudentCouncilNoticeDetailResponse response =
                studentCouncilNoticeService.createNotice(request);
        return ResponseEntity.created(
                        URI.create("/api/notices/categories/admin/" + response.id())
                )
                .body(response);
    }

    @Operation(
            summary = "사진·첨부파일을 포함한 학생회 공지 등록",
            description = "request JSON 파트와 images, attachments 파일 파트를 반복해서 전송합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값 또는 파일",
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
    public ResponseEntity<StudentCouncilNoticeDetailResponse> createNoticeWithFiles(
            @Valid @RequestPart("request") StudentCouncilNoticeRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestPart(value = "attachments", required = false)
            List<MultipartFile> attachments
    ) {
        StudentCouncilNoticeDetailResponse response =
                studentCouncilNoticeService.createNotice(request, images, attachments);
        return ResponseEntity.created(
                        URI.create("/api/notices/categories/admin/" + response.id())
                )
                .body(response);
    }

    @Operation(summary = "학생회 공지 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값 또는 공지 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "학생회 공지 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping(value = "/{noticeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StudentCouncilNoticeDetailResponse updateNotice(
            @Positive(message = "공지 ID는 1 이상이어야 합니다.") @PathVariable Long noticeId,
            @Valid @RequestBody StudentCouncilNoticeRequest request
    ) {
        return studentCouncilNoticeService.updateNotice(noticeId, request);
    }

    @Operation(
            summary = "사진·첨부파일을 포함한 학생회 공지 수정",
            description = "request의 retained ID 목록으로 기존 파일을 유지·삭제하고 새 파일을 추가합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값, 공지 ID 또는 파일 ID",
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
                    description = "학생회 공지 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping(value = "/{noticeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StudentCouncilNoticeDetailResponse updateNoticeWithFiles(
            @Positive(message = "공지 ID는 1 이상이어야 합니다.") @PathVariable Long noticeId,
            @Valid @RequestPart("request") StudentCouncilNoticeMultipartRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestPart(value = "attachments", required = false)
            List<MultipartFile> attachments
    ) {
        return studentCouncilNoticeService.updateNotice(
                noticeId,
                request,
                images,
                attachments
        );
    }

    @Operation(summary = "학생회 공지 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 공지 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "학생회 공지 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<Void> deleteNotice(
            @Positive(message = "공지 ID는 1 이상이어야 합니다.") @PathVariable Long noticeId
    ) {
        studentCouncilNoticeService.deleteNotice(noticeId);
        return ResponseEntity.noContent().build();
    }
}
