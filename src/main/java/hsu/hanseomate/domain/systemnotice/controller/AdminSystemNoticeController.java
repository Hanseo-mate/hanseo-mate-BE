package hsu.hanseomate.domain.systemnotice.controller;

import hsu.hanseomate.domain.systemnotice.dto.SystemNoticeRequest;
import hsu.hanseomate.domain.systemnotice.dto.SystemNoticeResponse;
import hsu.hanseomate.domain.systemnotice.service.SystemNoticeService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
        name = "관리자 시스템 공지 관리",
        description = "ADMIN 권한으로 시스템 공지를 등록, 수정, 삭제합니다."
)
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/system-notices")
public class AdminSystemNoticeController {

    private final SystemNoticeService systemNoticeService;

    @Operation(summary = "시스템 공지 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값",
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
    @PostMapping
    public ResponseEntity<SystemNoticeResponse> createNotice(
            @Valid @RequestBody SystemNoticeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(systemNoticeService.createNotice(request));
    }

    @Operation(summary = "시스템 공지 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값 또는 공지 ID",
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
                    description = "시스템 공지 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/{noticeId}")
    public SystemNoticeResponse updateNotice(
            @Parameter(description = "시스템 공지 ID", required = true)
            @Positive(message = "공지 ID는 1 이상이어야 합니다.")
            @PathVariable Long noticeId,
            @Valid @RequestBody SystemNoticeRequest request
    ) {
        return systemNoticeService.updateNotice(noticeId, request);
    }

    @Operation(summary = "시스템 공지 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
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
                    description = "시스템 공지 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<Void> deleteNotice(
            @Parameter(description = "시스템 공지 ID", required = true)
            @Positive(message = "공지 ID는 1 이상이어야 합니다.")
            @PathVariable Long noticeId
    ) {
        systemNoticeService.deleteNotice(noticeId);
        return ResponseEntity.noContent().build();
    }
}
