package hsu.hanseomate.domain.appupdate.service;

import hsu.hanseomate.domain.appupdate.dto.AppUpdateRequests.*;
import hsu.hanseomate.domain.appupdate.dto.AppUpdateResponses.AdminPolicy;
import hsu.hanseomate.domain.appupdate.support.AppUpdateRequestContext;
import hsu.hanseomate.domain.appupdate.type.*;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppUpdatePolicyOperations {
    private final AppUpdatePolicyService service;
    private final AppUpdateAuditService audit;

    public AdminPolicy create(Create request, AppUpdateRequestContext context) {
        return execute(AppUpdateEventType.CREATE, null, request.platform(), request.reason(), context,
                () -> service.create(request, context));
    }

    public AdminPolicy update(long id, Update request, AppUpdateRequestContext context) {
        return execute(AppUpdateEventType.UPDATE, id, null, request.reason(), context,
                () -> service.update(id, request, context));
    }

    public AdminPolicy publish(long id, Publish request, String key, AppUpdateRequestContext context) {
        return execute(request.publishMode() == PublishMode.SCHEDULED ? AppUpdateEventType.SCHEDULE : AppUpdateEventType.PUBLISH,
                id, null, request.reason(), context, () -> service.publish(id, request, key, context));
    }

    public AdminPolicy cancel(long id, Cancel request, String key, AppUpdateRequestContext context) {
        return execute(AppUpdateEventType.CANCEL, id, null, request.reason(), context,
                () -> service.cancel(id, request, key, context));
    }

    public AdminPolicy rollback(long id, Rollback request, String key, AppUpdateRequestContext context) {
        return execute(AppUpdateEventType.ROLLBACK, id, null, request.reason(), context,
                () -> service.rollback(id, request, key, context));
    }

    public AdminPolicy relax(long id, Relax request, String key, AppUpdateRequestContext context) {
        return execute(AppUpdateEventType.RELAX, id, null, request.reason(), context,
                () -> service.relax(id, request, key, context));
    }

    public boolean activateDue(long id) {
        AppUpdateRequestContext context = AppUpdateRequestContext.scheduler();
        return execute(AppUpdateEventType.ACTIVATE, id, null, "예약 시각 도래에 따른 서버 자동 적용",
                context, () -> service.activateDue(id, context));
    }

    private <T> T execute(AppUpdateEventType event, Long id, AppPlatform platform, String reason,
                          AppUpdateRequestContext context, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RuntimeException failure) {
            try {
                audit.failure(event, id, platform, context, reason, failure);
            } catch (RuntimeException auditFailure) {
                log.error("App update failure audit unavailable: event={}, policyId={}, requestId={}",
                        event, id, context.requestId(), auditFailure);
            }
            throw failure;
        }
    }
}
