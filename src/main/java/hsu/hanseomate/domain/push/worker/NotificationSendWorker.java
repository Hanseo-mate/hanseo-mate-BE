package hsu.hanseomate.domain.push.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.push.client.ExpoPushClient;
import hsu.hanseomate.domain.push.client.ExpoPushMessage;
import hsu.hanseomate.domain.push.client.ExpoTicketData;
import hsu.hanseomate.domain.push.dto.NotificationPayload;
import hsu.hanseomate.domain.push.entity.NotificationOutbox;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.entity.PushTicket;
import hsu.hanseomate.domain.push.service.NotificationDispatchService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 가이드 12번 '권장 서버 동작 순서' — 발송 단계 구현.
 *
 * <ol>
 *   <li>PENDING Outbox를 PROCESSING으로 전환</li>
 *   <li>활성화된 Push Device(토큰) 목록 조회</li>
 *   <li>플랫폼별 Expo payload 생성</li>
 *   <li>100개 단위로 Expo Push API 호출</li>
 *   <li>Push Ticket 저장</li>
 *   <li>Outbox 상태 SENT / FAILED 처리</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSendWorker {

    private static final String ANDROID_CHANNEL_ID = "general-v1";
    private static final String IOS_SOUND = "default";
    private static final String HIGH_PRIORITY = "high";
    private static final int CHUNK_SIZE = 100;

    private final NotificationDispatchService dispatchService;
    private final ExpoPushClient expoPushClient;
    private final ObjectMapper objectMapper;

    /**
     * 30초마다 실행. PENDING Outbox를 일괄 처리합니다.
     * fixedDelay를 사용하여 이전 실행이 완료된 후 30초 뒤에 다시 실행합니다.
     */
    @Scheduled(fixedDelay = 30_000)
    public void processPendingNotifications() {
        List<NotificationOutbox> outboxes = dispatchService.claimPendingOutboxes();
        if (outboxes.isEmpty()) return;

        log.info("Processing {} pending notification outbox(es)", outboxes.size());

        List<PushDevice> activeDevices = dispatchService.findAllActiveDevices();
        if (activeDevices.isEmpty()) {
            log.info("No active devices. Marking {} outbox(es) as SENT without dispatch.", outboxes.size());
            outboxes.forEach(o -> dispatchService.markOutboxSent(o.getId()));
            return;
        }

        for (NotificationOutbox outbox : outboxes) {
            processOutbox(outbox, activeDevices);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void processOutbox(NotificationOutbox outbox, List<PushDevice> activeDevices) {
        try {
            NotificationPayload payload = objectMapper.readValue(outbox.getPayload(), NotificationPayload.class);
            dispatchToDevices(outbox.getId(), payload, activeDevices);
            dispatchService.markOutboxSent(outbox.getId());
            log.info("Outbox id={} sent to {} device(s)", outbox.getId(), activeDevices.size());
        } catch (JsonProcessingException e) {
            String msg = "Failed to parse outbox payload: " + e.getMessage();
            log.error("Outbox id={} parse error: {}", outbox.getId(), e.getMessage());
            dispatchService.markOutboxFailed(outbox.getId(), msg);
        } catch (Exception e) {
            String msg = e.getMessage();
            log.error("Outbox id={} dispatch error: {}", outbox.getId(), msg, e);
            dispatchService.markOutboxFailed(outbox.getId(), msg);
        }
    }

    private void dispatchToDevices(
            Long outboxId,
            NotificationPayload payload,
            List<PushDevice> devices
    ) {
        // 디바이스 순서를 보존하여 Ticket 매핑에 활용
        List<PushDevice> orderedDevices = new ArrayList<>(devices);
        List<ExpoPushMessage> messages = new ArrayList<>(devices.size());

        for (PushDevice device : orderedDevices) {
            messages.add(buildMessage(device, payload));
        }

        // 100개 단위로 청크 분할 후 발송 (ExpoPushClient도 내부에서 분할하지만,
        // 여기서 직접 분할하여 Device-Ticket 1:1 매핑을 유지합니다)
        int total = messages.size();
        for (int i = 0; i < total; i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, total);
            List<ExpoPushMessage> chunk = messages.subList(i, end);
            List<PushDevice> chunkDevices = orderedDevices.subList(i, end);

            List<ExpoTicketData> ticketDataList = expoPushClient.sendMessages(chunk);
            List<PushTicket> tickets = buildTickets(outboxId, chunkDevices, ticketDataList);
            dispatchService.saveTickets(tickets);
        }
    }

    private ExpoPushMessage buildMessage(PushDevice device, NotificationPayload payload) {
        ExpoPushMessage.ExpoPushMessageBuilder builder = ExpoPushMessage.builder()
                .to(device.getExpoPushToken())
                .title(payload.getTitle())
                .body(payload.getBody())
                .priority(HIGH_PRIORITY)
                .data(payload.getData());

        // 플랫폼별 페이로드 분기 (가이드 7번)
        if ("android".equalsIgnoreCase(device.getPlatform())) {
            builder.channelId(ANDROID_CHANNEL_ID);
            // Android 8+에서는 채널이 소리/진동을 관리하므로 sound 생략
        } else if ("ios".equalsIgnoreCase(device.getPlatform())) {
            builder.sound(IOS_SOUND);
        }

        return builder.build();
    }

    private List<PushTicket> buildTickets(
            Long outboxId,
            List<PushDevice> chunkDevices,
            List<ExpoTicketData> ticketDataList
    ) {
        List<PushTicket> tickets = new ArrayList<>();
        int size = Math.min(chunkDevices.size(), ticketDataList.size());

        for (int i = 0; i < size; i++) {
            ExpoTicketData ticketData = ticketDataList.get(i);
            PushDevice device = chunkDevices.get(i);

            if ("ok".equals(ticketData.getStatus()) && ticketData.getId() != null) {
                tickets.add(PushTicket.create(ticketData.getId(), outboxId, device.getId()));
            } else {
                String errCode = ticketData.getDetails() != null
                        ? ticketData.getDetails().getError() : null;
                log.warn("Ticket error deviceId={} status={} error={}",
                        device.getId(), ticketData.getStatus(), errCode);
            }
        }
        return tickets;
    }
}
