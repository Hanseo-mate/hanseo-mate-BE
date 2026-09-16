package hsu.hanseomate.domain.appupdate.controller;

import hsu.hanseomate.domain.appupdate.dto.AppUpdateRequests;
import hsu.hanseomate.domain.appupdate.dto.AppUpdateResponses.*;
import hsu.hanseomate.domain.appupdate.service.AppUpdatePolicyOperations;
import hsu.hanseomate.domain.appupdate.service.AppUpdatePolicyService;
import hsu.hanseomate.domain.appupdate.support.AppUpdateRequestContext;
import hsu.hanseomate.domain.appupdate.support.AppUpdateValidator;
import hsu.hanseomate.domain.appupdate.type.*;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자 앱 업데이트 정책", description = "기존 ADMIN JWT 권한으로 초안, 게시, 예약, 롤백과 감사 이력을 관리합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/app-update-policies")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "필드 검증 실패",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "revision, 현재 활성 정책, 상태 또는 멱등 키 충돌",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class AdminAppUpdatePolicyController {
    private final AppUpdatePolicyService service;
    private final AppUpdatePolicyOperations operations;

    @Operation(summary = "현재, 예약, 초안 및 과거 정책 목록")
    @ApiResponse(responseCode = "200", description = "정책 목록 조회 성공")
    @GetMapping
    public PageResponse<AdminPolicy> list(@RequestParam(required = false) AppPlatform platform,
                                         @RequestParam(required = false) AppUpdateStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(platform, status, page, size);
    }

    @Operation(summary = "정책 상세 및 처리 결과 재조회")
    @ApiResponse(responseCode = "200", description = "정책 상세 조회 성공")
    @GetMapping("/{policyId}")
    public AdminPolicy get(@PathVariable long policyId) {
        return service.get(policyId);
    }

    @Operation(summary = "정책 변경 감사 이력", description = "from은 포함하고 to는 포함하지 않는 UTC 범위입니다. actorId가 null인 항목은 서버 예약 적용입니다.")
    @ApiResponse(responseCode = "200", description = "감사 이력 조회 성공")
    @GetMapping("/audit-logs")
    public PageResponse<Audit> audits(@RequestParam(required = false) AppPlatform platform,
                                     @RequestParam(required = false) AppUpdateEventType eventType,
                                     @RequestParam(required = false) Long actorId,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return service.auditLogs(platform, eventType, actorId,
                AppUpdateValidator.utc(from, "from"), AppUpdateValidator.utc(to, "to"), page, size);
    }

    @Operation(summary = "새 초안 생성")
    @ApiResponse(responseCode = "201", description = "초안 생성 성공")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdminPolicy> create(@RequestBody AppUpdateRequests.Create body,
                                              Authentication auth, HttpServletRequest request) {
        AdminPolicy result = operations.create(body, AppUpdateRequestContext.from(auth, request));
        return ResponseEntity.created(URI.create("/api/admin/app-update-policies/" + result.id())).body(result);
    }

    @Operation(summary = "초안 수정", description = "플랫폼은 바꿀 수 없으며 DRAFT와 일치하는 revision만 수정할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "초안 수정 성공")
    @PutMapping(value = "/{policyId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminPolicy update(@PathVariable long policyId, @RequestBody AppUpdateRequests.Update body,
                              Authentication auth, HttpServletRequest request) {
        return operations.update(policyId, body, AppUpdateRequestContext.from(auth, request));
    }

    @Operation(summary = "저장된 정책의 서버 판정 미리보기")
    @ApiResponse(responseCode = "200", description = "미리보기 성공",
            content = @Content(schema = @Schema(implementation = Preview.class)))
    @PostMapping(value = "/{policyId}/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Preview preview(@PathVariable long policyId, @RequestBody AppUpdateRequests.Preview body) {
        return service.preview(policyId, body);
    }

    @Operation(summary = "즉시 또는 예약 게시", description = "Idempotency-Key는 같은 본문 재시도에 재사용합니다. 스토어 배포 확인은 필수입니다.")
    @ApiResponse(responseCode = "200", description = "게시 성공 또는 최초 게시 결과 재전송")
    @PostMapping(value = "/{policyId}/publish", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminPolicy publish(@PathVariable long policyId, @RequestBody AppUpdateRequests.Publish body,
                               @RequestHeader("Idempotency-Key") String key,
                               Authentication auth, HttpServletRequest request) {
        return operations.publish(policyId, body, key, AppUpdateRequestContext.from(auth, request));
    }

    @Operation(summary = "예약 취소")
    @ApiResponse(responseCode = "200", description = "예약 취소 성공 또는 최초 취소 결과 재전송")
    @PostMapping(value = "/{policyId}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminPolicy cancel(@PathVariable long policyId, @RequestBody AppUpdateRequests.Cancel body,
                              @RequestHeader("Idempotency-Key") String key,
                              Authentication auth, HttpServletRequest request) {
        return operations.cancel(policyId, body, key, AppUpdateRequestContext.from(auth, request));
    }

    @Operation(summary = "과거 게시 정책으로 롤백", description = "SUPERSEDED 정책을 복제한 새 정책을 즉시 활성화합니다. 남은 예약은 함께 취소합니다.")
    @ApiResponse(responseCode = "200", description = "롤백 성공 또는 최초 롤백 결과 재전송")
    @PostMapping(value = "/{policyId}/rollback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminPolicy rollback(@PathVariable long policyId, @RequestBody AppUpdateRequests.Rollback body,
                                @RequestHeader("Idempotency-Key") String key,
                                Authentication auth, HttpServletRequest request) {
        return operations.rollback(policyId, body, key, AppUpdateRequestContext.from(auth, request));
    }

    @Operation(summary = "새 초안으로 긴급 완화", description = "현재 최소 지원 빌드를 낮추거나 강제 업데이트를 끄는 초안을 즉시 적용합니다. 현재 활성 ID와 revision을 확인하고 예약은 함께 취소합니다.")
    @ApiResponse(responseCode = "200", description = "긴급 완화 성공 또는 최초 완화 결과 재전송")
    @PostMapping(value = "/{policyId}/relax", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminPolicy relax(@PathVariable long policyId, @RequestBody AppUpdateRequests.Relax body,
                             @RequestHeader("Idempotency-Key") String key,
                             Authentication auth, HttpServletRequest request) {
        return operations.relax(policyId, body, key, AppUpdateRequestContext.from(auth, request));
    }
}
