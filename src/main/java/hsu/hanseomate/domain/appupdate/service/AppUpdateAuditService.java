package hsu.hanseomate.domain.appupdate.service;

import hsu.hanseomate.domain.appupdate.dto.AppUpdateResponses.*;
import hsu.hanseomate.domain.appupdate.entity.AppUpdateAudit;
import hsu.hanseomate.domain.appupdate.entity.AppUpdatePolicy;
import hsu.hanseomate.domain.appupdate.exception.AppUpdateException;
import hsu.hanseomate.domain.appupdate.repository.AppUpdateAuditRepository;
import hsu.hanseomate.domain.appupdate.repository.AppUpdatePolicyRepository;
import hsu.hanseomate.domain.appupdate.support.AppUpdateRequestContext;
import hsu.hanseomate.domain.appupdate.support.AppUpdateValidator;
import hsu.hanseomate.domain.appupdate.type.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AppUpdateAuditService {
    private final AppUpdateAuditRepository audits;
    private final AppUpdatePolicyRepository policies;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void success(AppUpdateEventType event, State before, State after,
                        AppUpdateRequestContext context, String reason) {
        AdminPolicy policy = after.policy();
        audits.saveAndFlush(new AppUpdateAudit(
                policy.id(), policy.revision(), policy.platform(), event, true,
                mapper.writeValueAsString(before), mapper.writeValueAsString(after),
                context.actorId(), policy.publishedBy(), policy.publishedAt(),
                clock.instant().truncatedTo(ChronoUnit.MICROS), reason,
                context.requestIp(), context.userAgent(), context.requestId(), null));
    }

    // 실패한 정책 트랜잭션이 종료된 뒤 별도 트랜잭션에 시도 이력만 남긴다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(AppUpdateEventType event, Long policyId, AppPlatform platform,
                        AppUpdateRequestContext context, String reason, RuntimeException failure) {
        AppUpdatePolicy current = policyId == null ? null : policies.findById(policyId).orElse(null);
        AdminPolicy snapshot = AdminPolicy.from(current);
        String message = failure instanceof AppUpdateException ? failure.getMessage() : "정책 처리 중 서버 오류";
        audits.saveAndFlush(new AppUpdateAudit(
                policyId, current == null ? null : current.getRevision(),
                current == null ? platform : current.getPlatform(), event, false,
                snapshot == null ? null : mapper.writeValueAsString(snapshot), null,
                context.actorId(), current == null ? null : current.getPublishedBy(),
                current == null ? null : current.getPublishedAt(),
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                failureReason(reason),
                context.requestIp(), context.userAgent(), context.requestId(),
                AppUpdateRequestContext.safe(message, 1000)));
    }

    private static String failureReason(String reason) {
        try {
            return AppUpdateValidator.reason(reason);
        } catch (AppUpdateException invalidReason) {
            return "유효하지 않은 변경 사유로 요청 실패";
        }
    }
}
