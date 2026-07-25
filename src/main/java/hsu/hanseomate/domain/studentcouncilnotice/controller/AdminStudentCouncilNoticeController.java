package hsu.hanseomate.domain.studentcouncilnotice.controller;

import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeDetailResponse;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeRequest;
import hsu.hanseomate.domain.studentcouncilnotice.service.StudentCouncilNoticeService;
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
        name = "관리자 학생회 공지 관리",
        description = "학생회 공지를 등록, 수정, 삭제합니다. 현재는 인증을 적용하지 않습니다."
)
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/notices")
public class AdminStudentCouncilNoticeController {

    private final StudentCouncilNoticeService studentCouncilNoticeService;

    @Operation(summary = "학생회 공지 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping
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
    @PutMapping("/{noticeId}")
    public StudentCouncilNoticeDetailResponse updateNotice(
            @Positive(message = "공지 ID는 1 이상이어야 합니다.") @PathVariable Long noticeId,
            @Valid @RequestBody StudentCouncilNoticeRequest request
    ) {
        return studentCouncilNoticeService.updateNotice(noticeId, request);
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
