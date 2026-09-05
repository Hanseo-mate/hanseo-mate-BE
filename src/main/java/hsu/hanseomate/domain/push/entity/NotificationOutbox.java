package hsu.hanseomate.domain.push.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 알림 발송 요청 큐 테이블.
 * 공지 저장·크롤링 이벤트 발생 시 즉시 푸시를 발송하지 않고
 * 이 테이블에 먼저 기록한 뒤 Worker가 처리합니다(Outbox 패턴).
 */
@Getter
@Entity
@Table(name = "notification_outbox", indexes = @Index(
        name = "idx_notification_outbox_status", columnList = "status,id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 알림 내용 JSON (title, body, data 포함).
     * {@link hsu.hanseomate.domain.push.dto.NotificationPayload} 구조로 직렬화.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** null이면 모든 활성 기기, 값이 있으면 해당 사용자의 활성 기기에만 발송합니다. */
    @Column(name = "target_user_id")
    private Long targetUserId;

    /** UTC 기준 발송 유효 기한. null인 기존 일반 알림에는 만료가 적용되지 않습니다. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public static NotificationOutbox create(String payload) {
        return create(payload, null);
    }

    public static NotificationOutbox create(String payload, Long targetUserId) {
        return create(payload, targetUserId, null);
    }

    public static NotificationOutbox create(String payload, Long targetUserId, LocalDateTime expiresAt) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.payload = payload;
        outbox.targetUserId = targetUserId;
        outbox.expiresAt = expiresAt;
        outbox.status = OutboxStatus.PENDING;
        return outbox;
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
    }

    public boolean isExpiredAt(LocalDateTime utcNow) {
        return expiresAt != null && !expiresAt.isAfter(utcNow);
    }

    public void markExpired() {
        this.status = OutboxStatus.EXPIRED;
    }

    public void markFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = truncate(errorMessage, 500);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
