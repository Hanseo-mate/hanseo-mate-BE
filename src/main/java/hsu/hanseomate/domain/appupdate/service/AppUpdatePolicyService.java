package hsu.hanseomate.domain.appupdate.service;

import static hsu.hanseomate.domain.appupdate.exception.AppUpdateException.*;
import static hsu.hanseomate.domain.appupdate.support.AppUpdateValidator.*;
import static hsu.hanseomate.domain.appupdate.type.AppUpdateStatus.*;

import hsu.hanseomate.domain.appupdate.dto.AppUpdateRequests;
import hsu.hanseomate.domain.appupdate.dto.AppUpdateResponses.*;
import hsu.hanseomate.domain.appupdate.dto.PolicyContent;
import hsu.hanseomate.domain.appupdate.entity.*;
import hsu.hanseomate.domain.appupdate.repository.*;
import hsu.hanseomate.domain.appupdate.support.*;
import hsu.hanseomate.domain.appupdate.type.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class AppUpdatePolicyService {
    private final AppUpdatePolicyRepository policies;
    private final AppUpdatePlatformLockRepository platformLocks;
    private final AppUpdateCommandRepository commands;
    private final AppUpdateAuditRepository auditRepository;
    private final AppUpdateAuditService audit;
    private final AppUpdateValidator validator;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final MeterRegistry metrics;

    public Check check(AppPlatform platform, Long build, String version) {
        if (platform == null) throw badRequest("platform: IOS 또는 ANDROID가 필요합니다.");
        positive(build, "build");
        String displayVersion = plainText(version, 1, 32, "version");
        AppUpdatePolicy active = policies.findByPlatformAndStatus(platform, ACTIVE).orElse(null);
        AppUpdateAction action = AppUpdateDecision.decide(active == null ? null : active.content(), build);
        metrics.counter("app.update.checks", "platform", platform.name(), "action", action.name()).increment();
        log.info("App update check: platform={}, build={}, version={}, action={}, policyRevision={}",
                platform, build, displayVersion, action, active == null ? null : active.getRevision());
        return new Check(action, active == null ? null : Policy.from(active), now());
    }

    public PageResponse<AdminPolicy> list(AppPlatform platform, AppUpdateStatus status, int page, int size) {
        Specification<AppUpdatePolicy> filter = (root, query, cb) -> cb.conjunction();
        if (platform != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("platform"), platform));
        if (status != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return PageResponse.from(policies.findAll(filter, pageRequest(page, size)).map(AdminPolicy::from));
    }

    public AdminPolicy get(long id) {
        return AdminPolicy.from(policies.findById(id).orElseThrow(() -> notFound()));
    }

    public PageResponse<Audit> auditLogs(AppPlatform platform, AppUpdateEventType event, Long actorId,
                                        Instant from, Instant to, int page, int size) {
        if (from != null && to != null && !from.isBefore(to)) throw badRequest("from: to보다 이전이어야 합니다.");
        if (actorId != null) positive(actorId, "actorId");
        Specification<AppUpdateAudit> filter = (root, query, cb) -> cb.conjunction();
        if (platform != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("platform"), platform));
        if (event != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("eventType"), event));
        if (actorId != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        if (from != null) filter = filter.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null) filter = filter.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), to));
        return PageResponse.from(auditRepository.findAll(filter, pageRequest(page, size)).map(a -> Audit.from(a, mapper)));
    }

    public Preview preview(long id, AppUpdateRequests.Preview request) {
        AppUpdatePolicy policy = policies.findById(id).orElseThrow(() -> notFound());
        revision(request.revision(), policy.getRevision());
        if (request.builds() == null || request.builds().isEmpty() || request.builds().size() > 100) {
            throw badRequest("builds: 1~100개의 빌드를 입력해 주세요.");
        }
        List<Decision> results = request.builds().stream().map(build -> new Decision(
                positive(build, "builds"), AppUpdateDecision.decide(policy.content(), build))).toList();
        return new Preview(id, policy.getRevision(), results);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminPolicy create(AppUpdateRequests.Create request, AppUpdateRequestContext context) {
        PolicyContent content = validator.content(request.platform(), request);
        String reason = reason(request.reason());
        platformLocks.acquire(request.platform().name());
        AppUpdatePolicy active = current(request.platform(), ACTIVE);
        AppUpdatePolicy scheduled = current(request.platform(), SCHEDULED);
        State before = state(null, active, scheduled);
        AppUpdatePolicy policy = policies.saveAndFlush(
                AppUpdatePolicy.draft(request.platform(), content, context.actorId(), now()));
        audit.success(AppUpdateEventType.CREATE, before, state(policy, active, scheduled), context, reason);
        return AdminPolicy.from(policy);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminPolicy update(long id, AppUpdateRequests.Update request, AppUpdateRequestContext context) {
        AppUpdatePolicy policy = lockedPolicy(id);
        requireStatus(policy, DRAFT);
        revision(request.revision(), policy.getRevision());
        PolicyContent content = validator.content(policy.getPlatform(), request);
        String reason = reason(request.reason());
        AppUpdatePolicy active = current(policy.getPlatform(), ACTIVE);
        AppUpdatePolicy scheduled = current(policy.getPlatform(), SCHEDULED);
        State before = state(policy, active, scheduled);
        policy.updateDraft(content, context.actorId(), now());
        policies.flush();
        audit.success(AppUpdateEventType.UPDATE, before, state(policy, active, scheduled), context, reason);
        return AdminPolicy.from(policy);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminPolicy publish(long id, AppUpdateRequests.Publish request, String key, AppUpdateRequestContext context) {
        AppUpdateCommand command = reserve(key, "publish", id, request, context);
        if (command.getResponseJson() != null) return replay(command);
        AppUpdatePolicy policy = lockedPolicy(id);
        requireStatus(policy, DRAFT);
        revision(request.revision(), policy.getRevision());
        String reason = reason(request.reason());
        confirmed(request.storeAvailabilityConfirmed());
        validator.storeUrl(policy.getPlatform(), policy.getStoreUrl());
        if (request.publishMode() == null) throw badRequest("publishMode: IMMEDIATE 또는 SCHEDULED가 필요합니다.");
        Instant now = now();
        boolean scheduledMode = request.publishMode() == PublishMode.SCHEDULED;
        Instant effectiveAt = request.effectiveAt() == null ? null : request.effectiveAt().truncatedTo(ChronoUnit.MICROS);
        if (scheduledMode ? effectiveAt == null || !effectiveAt.isAfter(now) : effectiveAt != null) {
            throw badRequest("effectiveAt: 즉시는 null, 예약은 현재보다 미래의 UTC 시각이어야 합니다.");
        }
        if (scheduledMode && effectiveAt.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))) {
            throw badRequest("effectiveAt: DB에서 지원하는 UTC 시각 범위를 초과했습니다.");
        }
        AppUpdatePolicy active = current(policy.getPlatform(), ACTIVE);
        AppUpdatePolicy scheduled = current(policy.getPlatform(), SCHEDULED);
        if (scheduled != null) throw conflict("예약된 정책이 있습니다. 예약 취소 후 게시해 주세요.");
        if (lowersMinimum(policy, active)) {
            throw conflict("최소 지원 빌드를 낮추려면 긴급 완화(relax) 또는 롤백 API를 사용해 주세요.");
        }
        State before = state(policy, active, scheduled);
        if (!scheduledMode) supersede(active, context.actorId(), now);
        policy.publish(scheduledMode, scheduledMode ? effectiveAt : now, context.actorId(), now);
        policies.flush();
        audit.success(scheduledMode ? AppUpdateEventType.SCHEDULE : AppUpdateEventType.PUBLISH,
                before, state(policy, active, scheduled), context, reason);
        return complete(command, policy);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminPolicy cancel(long id, AppUpdateRequests.Cancel request, String key, AppUpdateRequestContext context) {
        AppUpdateCommand command = reserve(key, "cancel", id, request, context);
        if (command.getResponseJson() != null) return replay(command);
        AppUpdatePolicy policy = lockedPolicy(id);
        requireStatus(policy, SCHEDULED);
        revision(request.revision(), policy.getRevision());
        String reason = reason(request.reason());
        AppUpdatePolicy active = current(policy.getPlatform(), ACTIVE);
        State before = state(policy, active, policy);
        policy.cancel(context.actorId(), now());
        policies.flush();
        audit.success(AppUpdateEventType.CANCEL, before, state(policy, active, policy), context, reason);
        return complete(command, policy);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminPolicy rollback(long id, AppUpdateRequests.Rollback request, String key, AppUpdateRequestContext context) {
        AppUpdateCommand command = reserve(key, "rollback", id, request, context);
        if (command.getResponseJson() != null) return replay(command);
        AppUpdatePolicy source = lockedPolicy(id);
        requireStatus(source, SUPERSEDED);
        String reason = reason(request.reason());
        AppUpdatePolicy active = expectedActive(source.getPlatform(),
                request.expectedActivePolicyId(), request.expectedActiveRevision());
        validator.storeUrl(source.getPlatform(), source.getStoreUrl());
        AppUpdatePolicy scheduled = current(source.getPlatform(), SCHEDULED);
        State before = state(source, active, scheduled);
        Instant now = now();
        cancelPending(scheduled, context.actorId(), now);
        supersede(active, context.actorId(), now);
        AppUpdatePolicy restored = policies.saveAndFlush(AppUpdatePolicy.restore(source, context.actorId(), now));
        audit.success(AppUpdateEventType.ROLLBACK, before, state(restored, active, scheduled), context, reason);
        return complete(command, restored);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminPolicy relax(long id, AppUpdateRequests.Relax request, String key, AppUpdateRequestContext context) {
        AppUpdateCommand command = reserve(key, "relax", id, request, context);
        if (command.getResponseJson() != null) return replay(command);
        AppUpdatePolicy policy = lockedPolicy(id);
        requireStatus(policy, DRAFT);
        revision(request.revision(), policy.getRevision());
        String reason = reason(request.reason());
        confirmed(request.storeAvailabilityConfirmed());
        validator.storeUrl(policy.getPlatform(), policy.getStoreUrl());
        AppUpdatePolicy active = expectedActive(policy.getPlatform(),
                request.expectedActivePolicyId(), request.expectedActiveRevision());
        if (!lowersMinimum(policy, active)) throw badRequest("긴급 완화는 현재 최소 지원 빌드를 낮추거나 강제 기능을 끄는 정책이어야 합니다.");
        AppUpdatePolicy scheduled = current(policy.getPlatform(), SCHEDULED);
        State before = state(policy, active, scheduled);
        Instant now = now();
        cancelPending(scheduled, context.actorId(), now);
        supersede(active, context.actorId(), now);
        policy.publish(false, now, context.actorId(), now);
        policies.flush();
        audit.success(AppUpdateEventType.RELAX, before, state(policy, active, scheduled), context, reason);
        return complete(command, policy);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean activateDue(long id, AppUpdateRequestContext context) {
        AppUpdatePolicy policy = lockedPolicy(id);
        Instant now = now();
        if (policy.getStatus() != SCHEDULED || policy.getEffectiveAt().isAfter(now)) return false;
        validator.storeUrl(policy.getPlatform(), policy.getStoreUrl());
        AppUpdatePolicy active = current(policy.getPlatform(), ACTIVE);
        State before = state(policy, active, policy);
        supersede(active, policy.getPublishedBy(), now);
        policy.activateScheduled(now);
        policies.flush();
        audit.success(AppUpdateEventType.ACTIVATE, before, state(policy, active, policy),
                context, "예약 시각 도래에 따른 서버 자동 적용");
        return true;
    }

    private AppUpdatePolicy lockedPolicy(long id) {
        positive(id, "policyId");
        AppPlatform platform = policies.findPlatformById(id).orElseThrow(() -> notFound());
        platformLocks.acquire(platform.name());
        // READ_COMMITTED 및 locking read로 잠금 대기 전에 읽은 오래된 상태를 사용하지 않는다.
        return policies.findForUpdate(id).orElseThrow(() -> notFound());
    }

    private AppUpdatePolicy current(AppPlatform platform, AppUpdateStatus status) {
        return policies.findStatusForUpdate(platform, status).orElse(null);
    }

    private AppUpdatePolicy expectedActive(AppPlatform platform, Long expectedId, Long expectedRevision) {
        positive(expectedId, "expectedActivePolicyId");
        positive(expectedRevision, "expectedActiveRevision");
        AppUpdatePolicy active = current(platform, ACTIVE);
        if (active == null || !active.getId().equals(expectedId) || active.getRevision() != expectedRevision) {
            throw conflict("현재 활성 정책이 변경되었습니다. 최신 활성 정책을 조회해 주세요.");
        }
        return active;
    }

    private static boolean lowersMinimum(AppUpdatePolicy next, AppUpdatePolicy active) {
        return active != null && active.isForceUpdateEnabled()
                && (!next.isForceUpdateEnabled() || next.getMinimumSupportedBuild() < active.getMinimumSupportedBuild());
    }

    private void supersede(AppUpdatePolicy active, Long actorId, Instant now) {
        if (active == null) return;
        active.supersede(actorId, now);
        // 새 ACTIVE를 flush/insert하기 전에 유니크 슬롯을 먼저 비운다.
        policies.flush();
    }

    private void cancelPending(AppUpdatePolicy scheduled, Long actorId, Instant now) {
        if (scheduled == null) return;
        scheduled.cancel(actorId, now);
        policies.flush();
    }

    private static void requireStatus(AppUpdatePolicy policy, AppUpdateStatus status) {
        if (policy.getStatus() != status) throw conflict(status + " 정책만 처리할 수 있습니다. 현재 상태: " + policy.getStatus());
    }

    private static void confirmed(Boolean confirmed) {
        if (!Boolean.TRUE.equals(confirmed)) throw badRequest("storeAvailabilityConfirmed: 실제 스토어 배포 확인이 필요합니다.");
    }

    private AppUpdateCommand reserve(String key, String operation, long id, Object request, AppUpdateRequestContext context) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw badRequest("Idempotency-Key: 영문, 숫자, 점, 밑줄, 콜론, 하이픈 1~128자가 필요합니다.");
        }
        String fingerprint = fingerprint(mapper.writeValueAsString(List.of(context.actorId(), operation, id, request)));
        // 모든 명령에서 멱등 키 → 플랫폼 순서로 잠금을 획득해 교착을 피한다.
        commands.reserve(key, fingerprint, now());
        AppUpdateCommand command = commands.findForUpdate(key).orElseThrow();
        if (!command.getFingerprint().equals(fingerprint)) throw conflict("같은 Idempotency-Key를 다른 요청에 사용할 수 없습니다.");
        return command;
    }

    private AdminPolicy complete(AppUpdateCommand command, AppUpdatePolicy policy) {
        AdminPolicy response = AdminPolicy.from(policy);
        command.complete(mapper.writeValueAsString(response));
        commands.flush();
        return response;
    }

    private AdminPolicy replay(AppUpdateCommand command) {
        return mapper.readValue(command.getResponseJson(), AdminPolicy.class);
    }

    private static String fingerprint(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static State state(AppUpdatePolicy policy, AppUpdatePolicy previousActive, AppUpdatePolicy scheduled) {
        return new State(AdminPolicy.from(policy), AdminPolicy.from(previousActive), AdminPolicy.from(scheduled));
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw badRequest("page는 0 이상, size는 1~100이어야 합니다.");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
