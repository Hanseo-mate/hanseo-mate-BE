package hsu.hanseomate.domain.appupdate.entity;

import hsu.hanseomate.domain.appupdate.dto.PolicyContent;
import hsu.hanseomate.domain.appupdate.type.AppPlatform;
import hsu.hanseomate.domain.appupdate.type.AppUpdateStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "app_update_policies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_app_update_active", columnNames = "active_platform"),
                @UniqueConstraint(name = "uk_app_update_scheduled", columnNames = "scheduled_platform")
        },
        indexes = {
                @Index(name = "idx_app_update_list", columnList = "platform,status,id"),
                @Index(name = "idx_app_update_due", columnList = "status,effective_at")
        },
        check = @CheckConstraint(name = "chk_app_update_policy",
        constraint = "platform IN ('IOS','ANDROID') AND latest_build >= 1 AND revision >= 1"
        + " AND status IN ('DRAFT','SCHEDULED','ACTIVE','SUPERSEDED','CANCELLED')"
        + " AND ((force_update_enabled = false AND minimum_supported_build IS NULL)"
        + " OR (force_update_enabled = true AND minimum_supported_build IS NOT NULL"
        + " AND minimum_supported_build >= 1 AND minimum_supported_build <= latest_build))"
        + " AND ((status = 'ACTIVE' AND active_platform IS NOT NULL AND active_platform = platform)"
        + " OR (status <> 'ACTIVE' AND active_platform IS NULL))"
        + " AND ((status = 'SCHEDULED' AND scheduled_platform IS NOT NULL AND scheduled_platform = platform)"
        + " OR (status <> 'SCHEDULED' AND scheduled_platform IS NULL))"
        + " AND (status NOT IN ('ACTIVE','SCHEDULED','SUPERSEDED')"
        + " OR (effective_at IS NOT NULL AND published_at IS NOT NULL AND published_by IS NOT NULL"
        + " AND store_availability_confirmed_at IS NOT NULL AND store_availability_confirmed_by IS NOT NULL))"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUpdatePolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, columnDefinition = "varchar(16)")
    private AppPlatform platform;
    @Column(nullable = false, length = 32)
    private String latestVersion;
    @Column(nullable = false)
    private long latestBuild;
    @Column(nullable = false)
    private boolean forceUpdateEnabled;
    private Long minimumSupportedBuild;
    @Column(nullable = false)
    private boolean optionalUpdateEnabled;
    @Column(nullable = false, length = 500)
    private String storeUrl;
    @Column(nullable = false, length = 40)
    private String title;
    @Column(nullable = false, length = 300)
    private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private AppUpdateStatus status;
    @Enumerated(EnumType.STRING) @Column(length = 16, columnDefinition = "varchar(16)")
    private AppPlatform activePlatform;
    @Enumerated(EnumType.STRING) @Column(length = 16, columnDefinition = "varchar(16)")
    private AppPlatform scheduledPlatform;
    private Instant effectiveAt;
    // 모든 변경 경로가 플랫폼 DB 잠금을 먼저 획득한 뒤 요청 revision을 검사한다.
    @Column(nullable = false)
    private long revision;
    private Instant storeAvailabilityConfirmedAt;
    private Long storeAvailabilityConfirmedBy;
    @Column(nullable = false)
    private Long createdBy;
    @Column(nullable = false)
    private Long updatedBy;
    private Long publishedBy;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant publishedAt;

    public static AppUpdatePolicy draft(AppPlatform platform, PolicyContent content, long adminId, Instant now) {
        AppUpdatePolicy policy = new AppUpdatePolicy();
        policy.platform = platform;
        policy.applyContent(content);
        policy.setStatus(AppUpdateStatus.DRAFT);
        policy.revision = 1;
        policy.createdBy = adminId;
        policy.updatedBy = adminId;
        policy.createdAt = now;
        policy.updatedAt = now;
        return policy;
    }

    public PolicyContent content() {
        return new PolicyContent(latestVersion, latestBuild, forceUpdateEnabled, minimumSupportedBuild,
                optionalUpdateEnabled, storeUrl, title, message);
    }

    public void updateDraft(PolicyContent content, long adminId, Instant now) {
        applyContent(content);
        touch(adminId, now);
    }

    public void publish(boolean scheduled, Instant effectiveAt, long adminId, Instant now) {
        this.effectiveAt = effectiveAt;
        this.storeAvailabilityConfirmedAt = now;
        this.storeAvailabilityConfirmedBy = adminId;
        this.publishedBy = adminId;
        this.publishedAt = now;
        setStatus(scheduled ? AppUpdateStatus.SCHEDULED : AppUpdateStatus.ACTIVE);
        touch(adminId, now);
    }

    public void activateScheduled(Instant now) {
        setStatus(AppUpdateStatus.ACTIVE);
        touch(publishedBy, now);
    }

    public void supersede(Long adminId, Instant now) {
        setStatus(AppUpdateStatus.SUPERSEDED);
        touch(adminId, now);
    }

    public void cancel(Long adminId, Instant now) {
        setStatus(AppUpdateStatus.CANCELLED);
        touch(adminId, now);
    }

    public static AppUpdatePolicy restore(AppUpdatePolicy source, long adminId, Instant now) {
        AppUpdatePolicy restored = draft(source.platform, source.content(), adminId, now);
        restored.setStatus(AppUpdateStatus.ACTIVE);
        restored.effectiveAt = now;
        restored.publishedAt = now;
        restored.publishedBy = adminId;
        // 롤백은 기존 배포 확인 기록을 보존하며 새로운 배포 확인을 꾸며내지 않는다.
        restored.storeAvailabilityConfirmedAt = source.storeAvailabilityConfirmedAt;
        restored.storeAvailabilityConfirmedBy = source.storeAvailabilityConfirmedBy;
        return restored;
    }

    private void setStatus(AppUpdateStatus status) {
        this.status = status;
        this.activePlatform = status == AppUpdateStatus.ACTIVE ? platform : null;
        this.scheduledPlatform = status == AppUpdateStatus.SCHEDULED ? platform : null;
    }

    private void touch(Long adminId, Instant now) {
        updatedBy = adminId;
        updatedAt = now;
        revision++;
    }

    private void applyContent(PolicyContent content) {
        latestVersion = content.latestVersion();
        latestBuild = content.latestBuild();
        forceUpdateEnabled = content.forceUpdateEnabled();
        minimumSupportedBuild = content.minimumSupportedBuild();
        optionalUpdateEnabled = content.optionalUpdateEnabled();
        storeUrl = content.storeUrl();
        title = content.title();
        message = content.message();
    }
}
