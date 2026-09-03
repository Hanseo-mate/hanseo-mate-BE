package hsu.hanseomate.domain.push.service;

import hsu.hanseomate.domain.push.entity.NotificationOutbox;
import hsu.hanseomate.domain.push.entity.OutboxStatus;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.entity.PushTicket;
import hsu.hanseomate.domain.push.entity.TicketStatus;
import hsu.hanseomate.domain.push.repository.NotificationOutboxRepository;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.domain.push.repository.PushTicketRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Worker가 호출하는 트랜잭션 DB 연산 전용 서비스.
 *
 * <p>Worker(@Scheduled)와 서비스(@Service)를 분리함으로써
 * Spring AOP 프록시가 올바르게 동작하여 @Transactional이 적용됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationOutboxRepository outboxRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushTicketRepository pushTicketRepository;

    // ── Send Worker 전용 ───────────────────────────────────────────────────────

    /**
     * PENDING 상태 Outbox를 PROCESSING으로 전환하여 반환합니다.
     * 여러 인스턴스에서 동시에 실행되어도 중복 처리되지 않습니다.
     */
    @Transactional
    public List<NotificationOutbox> claimPendingOutboxes() {
        List<NotificationOutbox> pending = outboxRepository.findAllByStatus(OutboxStatus.PENDING);
        pending.forEach(NotificationOutbox::markProcessing);
        return pending;
    }

    /** 활성화된 모든 Push Device를 조회합니다. */
    @Transactional(readOnly = true)
    public List<PushDevice> findAllActiveDevices() {
        return pushDeviceRepository.findAllByIsActiveTrue();
    }

    /** 특정 로그인 사용자에게 연결된 활성 Push Device를 조회합니다. */
    @Transactional(readOnly = true)
    public List<PushDevice> findActiveDevicesByUserId(Long userId) {
        return pushDeviceRepository.findAllByUserIdAndIsActiveTrue(userId);
    }

    /** Outbox를 SENT로 완료 처리합니다. */
    @Transactional
    public void markOutboxSent(Long outboxId) {
        outboxRepository.findById(outboxId)
                .ifPresent(NotificationOutbox::markSent);
    }

    /** Outbox를 FAILED로 처리합니다. */
    @Transactional
    public void markOutboxFailed(Long outboxId, String errorMessage) {
        outboxRepository.findById(outboxId)
                .ifPresent(o -> o.markFailed(errorMessage));
    }

    /** 성공적으로 발급된 Ticket을 저장합니다. */
    @Transactional
    public void saveTickets(List<PushTicket> tickets) {
        if (!tickets.isEmpty()) {
            pushTicketRepository.saveAll(tickets);
        }
    }

    // ── Receipt Worker 전용 ───────────────────────────────────────────────────

    /**
     * 생성된 지 {@code minutesAgo}분이 지난 PENDING_RECEIPT 티켓을 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<PushTicket> findTicketsReadyForReceiptCheck(int minutesAgo) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutesAgo);
        return pushTicketRepository.findReadyForReceiptCheck(TicketStatus.PENDING_RECEIPT, threshold);
    }

    /** Ticket을 OK로 처리합니다. */
    @Transactional
    public void markTicketOk(Long ticketId) {
        pushTicketRepository.findById(ticketId)
                .ifPresent(PushTicket::markOk);
    }

    /**
     * Ticket을 ERROR로 처리하고, DeviceNotRegistered 에러인 경우 해당 기기를 비활성화합니다.
     */
    @Transactional
    public void handleTicketError(Long ticketId, Long pushDeviceId, String errorCode) {
        pushTicketRepository.findById(ticketId)
                .ifPresent(t -> t.markError(errorCode));

        if ("DeviceNotRegistered".equals(errorCode)) {
            pushDeviceRepository.findById(pushDeviceId)
                    .ifPresent(d -> {
                        d.deactivate(errorCode);
                        log.info("Deactivated device id={} due to DeviceNotRegistered", pushDeviceId);
                    });
        }
    }
}
