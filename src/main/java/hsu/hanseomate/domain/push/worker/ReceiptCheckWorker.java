package hsu.hanseomate.domain.push.worker;

import hsu.hanseomate.domain.push.client.ExpoPushClient;
import hsu.hanseomate.domain.push.client.ExpoReceiptResponse;
import hsu.hanseomate.domain.push.entity.PushTicket;
import hsu.hanseomate.domain.push.service.NotificationDispatchService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 가이드 12번 '권장 서버 동작 순서' — Receipt 조회 단계 구현.
 *
 * <ol>
 *   <li>생성된 지 15분 이상 경과한 PENDING_RECEIPT 티켓 조회</li>
 *   <li>Expo Receipt API 호출</li>
 *   <li>DeviceNotRegistered 에러 → 해당 기기 비활성화(is_active=false)</li>
 *   <li>Ticket 상태 OK / ERROR 업데이트</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiptCheckWorker {

    /** Receipt 조회 기준: 발송 후 최소 N분 경과 */
    private static final int RECEIPT_DELAY_MINUTES = 15;

    private final NotificationDispatchService dispatchService;
    private final ExpoPushClient expoPushClient;

    /**
     * 5분마다 실행. 15분 이상 경과한 PENDING_RECEIPT 티켓을 일괄 처리합니다.
     * Expo Receipt는 최대 24시간 보관되므로 그 전에 확인합니다.
     */
    @Scheduled(fixedDelay = 300_000) // 5분
    public void checkReceipts() {
        List<PushTicket> tickets =
                dispatchService.findTicketsReadyForReceiptCheck(RECEIPT_DELAY_MINUTES);

        if (tickets.isEmpty()) return;

        log.info("Checking receipts for {} ticket(s)", tickets.size());

        // Ticket ID 목록 추출
        List<String> ticketIds = tickets.stream()
                .map(PushTicket::getExpoTicketId)
                .collect(Collectors.toList());

        // Expo Receipt API 호출
        Map<String, ExpoReceiptResponse.ReceiptData> receiptMap;
        try {
            receiptMap = expoPushClient.getReceipts(ticketIds);
        } catch (Exception e) {
            log.error("Failed to fetch receipts: {}", e.getMessage(), e);
            return;
        }

        // 결과 처리
        for (PushTicket ticket : tickets) {
            ExpoReceiptResponse.ReceiptData receiptData =
                    receiptMap.get(ticket.getExpoTicketId());

            if (receiptData == null) {
                log.warn("No receipt found for ticketId={} expoTicketId={}",
                        ticket.getId(), ticket.getExpoTicketId());
                continue;
            }

            if (receiptData.isOk()) {
                dispatchService.markTicketOk(ticket.getId());
            } else {
                String errorCode = receiptData.getErrorCode();
                log.warn("Receipt error ticketId={} expoTicketId={} errorCode={}",
                        ticket.getId(), ticket.getExpoTicketId(), errorCode);
                dispatchService.handleTicketError(
                        ticket.getId(), ticket.getPushDeviceId(), errorCode);
            }
        }

        log.info("Receipt check complete: {} ticket(s) processed", tickets.size());
    }
}
