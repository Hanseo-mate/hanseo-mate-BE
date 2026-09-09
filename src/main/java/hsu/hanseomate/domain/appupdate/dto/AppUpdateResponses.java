package hsu.hanseomate.domain.appupdate.dto;

import hsu.hanseomate.domain.appupdate.entity.AppUpdateAudit;
import io.swagger.v3.oas.annotations.media.Schema;
import hsu.hanseomate.domain.appupdate.entity.AppUpdatePolicy;
import hsu.hanseomate.domain.appupdate.type.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class AppUpdateResponses {
    private AppUpdateResponses() {}

    @Schema(name = "AppUpdatePolicyResponse")
    public record Policy(
            long id, long revision, AppPlatform platform, String latestVersion, long latestBuild,
            boolean forceUpdateEnabled, Long minimumSupportedBuild, String storeUrl,
            String title, String message, Instant effectiveAt
    ) {
        public static Policy from(AppUpdatePolicy p) {
            return new Policy(p.getId(), p.getRevision(), p.getPlatform(), p.getLatestVersion(),
                    p.getLatestBuild(), p.isForceUpdateEnabled(), p.getMinimumSupportedBuild(),
                    p.getStoreUrl(), p.getTitle(), p.getMessage(), p.getEffectiveAt());
        }
    }

    @Schema(name = "AppUpdateCheckResponse")
    public record Check(AppUpdateAction action, Policy policy, Instant checkedAt) {}

    @Schema(name = "AppUpdatePolicyAdminResponse")
    public record AdminPolicy(
            long id, long revision, AppPlatform platform, String latestVersion, long latestBuild,
            boolean forceUpdateEnabled, Long minimumSupportedBuild, boolean optionalUpdateEnabled,
            String storeUrl, String title, String message, AppUpdateStatus status, Instant effectiveAt,
            Instant storeAvailabilityConfirmedAt, Long storeAvailabilityConfirmedBy, Long createdBy,
            Long updatedBy, Long publishedBy, Instant createdAt, Instant updatedAt, Instant publishedAt
    ) {
        public static AdminPolicy from(AppUpdatePolicy p) {
            if (p == null) return null;
            return new AdminPolicy(p.getId(), p.getRevision(), p.getPlatform(), p.getLatestVersion(),
                    p.getLatestBuild(), p.isForceUpdateEnabled(), p.getMinimumSupportedBuild(),
                    p.isOptionalUpdateEnabled(), p.getStoreUrl(), p.getTitle(), p.getMessage(), p.getStatus(),
                    p.getEffectiveAt(), p.getStoreAvailabilityConfirmedAt(), p.getStoreAvailabilityConfirmedBy(),
                    p.getCreatedBy(), p.getUpdatedBy(), p.getPublishedBy(),
                    p.getCreatedAt(), p.getUpdatedAt(), p.getPublishedAt());
        }
    }

    @Schema(name = "AppUpdatePolicyPreviewResponse")
    public record Preview(long policyId, long revision, List<Decision> results) {}
    public record Decision(long build, AppUpdateAction action) {}

    // 게시 대상뿐 아니라 함께 변경된 기존 활성/예약 정책 전체도 감사 스냅샷에 보존한다.
    public record State(AdminPolicy policy, AdminPolicy previousActivePolicy, AdminPolicy scheduledPolicy) {}

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages, boolean hasNext) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages(), page.hasNext());
        }
    }

    @Schema(name = "AppUpdatePolicyAuditResponse")
    public record Audit(
            long id, Long policyId, Long revision, AppPlatform platform, AppUpdateEventType eventType,
            boolean success, JsonNode beforeSnapshot, JsonNode afterSnapshot, Long actorId,
            Long publishedBy, Instant publishedAt, Instant createdAt, String reason, String requestIp,
            String userAgent, String requestId, String failureMessage
    ) {
        public static Audit from(AppUpdateAudit a, ObjectMapper mapper) {
            return new Audit(a.getId(), a.getPolicyId(), a.getPolicyRevision(), a.getPlatform(),
                    a.getEventType(), a.isSuccess(),
                    a.getBeforeSnapshot() == null ? null : mapper.readTree(a.getBeforeSnapshot()),
                    a.getAfterSnapshot() == null ? null : mapper.readTree(a.getAfterSnapshot()),
                    a.getActorId(), a.getPublishedBy(), a.getPublishedAt(), a.getCreatedAt(), a.getReason(),
                    a.getRequestIp(), a.getUserAgent(), a.getRequestId(), a.getFailureMessage());
        }
    }
}
