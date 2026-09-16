package hsu.hanseomate.domain.push.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Expo Push 발송 후 반환된 Ticket ID 저장 테이블.
 * 발송 약 15분 뒤 Receipt API를 호출하여 실제 FCM/APNs 전달 결과를 추적합니다.
 */
@Getter
@Entity
@Table(
        name = "push_tickets",
        indexes = @Index(
                name = "idx_push_tickets_push_device",
                columnList = "push_device_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushTicket extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Expo Push API가 발급한 Ticket ID */
    @Column(name = "expo_ticket_id", nullable = false, length = 100)
    private String expoTicketId;

    /** 발송을 트리거한 NotificationOutbox ID */
    @Column(name = "outbox_id", nullable = false)
    private Long outboxId;

    /** 발송 대상 PushDevice ID */
    @Column(name = "push_device_id", nullable = false)
    private Long pushDeviceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "push_device_id",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_push_tickets_push_device")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PushDevice pushDevice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;

    /** DeviceNotRegistered 등 Receipt 에러 코드 (nullable) */
    @Column(name = "error_code", length = 100)
    private String errorCode;

    /** Receipt API 조회 완료 시각 (nullable) */
    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    public static PushTicket create(String expoTicketId, Long outboxId, Long pushDeviceId) {
        PushTicket ticket = new PushTicket();
        ticket.expoTicketId = expoTicketId;
        ticket.outboxId = outboxId;
        ticket.pushDeviceId = pushDeviceId;
        ticket.status = TicketStatus.PENDING_RECEIPT;
        return ticket;
    }

    public void markOk() {
        this.status = TicketStatus.OK;
        this.checkedAt = LocalDateTime.now();
    }

    public void markError(String errorCode) {
        this.status = TicketStatus.ERROR;
        this.errorCode = errorCode;
        this.checkedAt = LocalDateTime.now();
    }
}
