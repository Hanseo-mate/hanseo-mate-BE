package hsu.hanseomate.domain.appupdate.entity;

import hsu.hanseomate.domain.appupdate.type.AppPlatform;
import hsu.hanseomate.domain.appupdate.type.AppUpdateEventType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Getter
@Entity
@Immutable
@Table(name = "app_update_audits", indexes = {
        @Index(name = "idx_app_update_audit_platform_time", columnList = "platform,created_at,id"),
        @Index(name = "idx_app_update_audit_actor_time", columnList = "actor_id,created_at"),
        @Index(name = "idx_app_update_audit_event_time", columnList = "event_type,created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUpdateAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long policyId;
    private Long policyRevision;
    @Enumerated(EnumType.STRING) @Column(length = 16, columnDefinition = "varchar(16)")
    private AppPlatform platform;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private AppUpdateEventType eventType;
    @Column(nullable = false)
    private boolean success;
    @Column(columnDefinition = "LONGTEXT")
    private String beforeSnapshot;
    @Column(columnDefinition = "LONGTEXT")
    private String afterSnapshot;
    private Long actorId;
    private Long publishedBy;
    private Instant publishedAt;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(length = 64)
    private String requestIp;
    @Column(length = 500)
    private String userAgent;
    @Column(nullable = false, length = 128)
    private String requestId;
    @Column(length = 1000)
    private String failureMessage;

    public AppUpdateAudit(Long policyId, Long policyRevision, AppPlatform platform,
                          AppUpdateEventType eventType, boolean success, String beforeSnapshot,
                          String afterSnapshot, Long actorId, Long publishedBy, Instant publishedAt,
                          Instant createdAt, String reason, String requestIp, String userAgent,
                          String requestId, String failureMessage) {
        this.policyId = policyId;
        this.policyRevision = policyRevision;
        this.platform = platform;
        this.eventType = eventType;
        this.success = success;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.actorId = actorId;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.reason = reason;
        this.requestIp = requestIp;
        this.userAgent = userAgent;
        this.requestId = requestId;
        this.failureMessage = failureMessage;
    }
}
