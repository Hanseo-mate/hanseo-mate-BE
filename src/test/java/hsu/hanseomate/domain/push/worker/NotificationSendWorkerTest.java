package hsu.hanseomate.domain.push.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.push.client.ExpoPushClient;
import hsu.hanseomate.domain.push.client.ExpoPushMessage;
import hsu.hanseomate.domain.push.dto.NotificationPayload;
import hsu.hanseomate.domain.push.entity.NotificationOutbox;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.service.NotificationDispatchService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationSendWorkerTest {

    @Test
    void targetedOutboxIsSentOnlyToActiveDevicesBelongingToTargetUser() throws Exception {
        NotificationDispatchService dispatchService = mock(NotificationDispatchService.class);
        ExpoPushClient expoPushClient = mock(ExpoPushClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationSendWorker worker = new NotificationSendWorker(
                dispatchService,
                expoPushClient,
                objectMapper
        );

        long targetUserId = 42L;
        String payload = objectMapper.writeValueAsString(new NotificationPayload(
                "[동아리 모집공고] 테스트 동아리",
                "새로운 모집공고가 등록되었습니다.",
                Map.of(
                        "version", 1,
                        "type", "club_recruitment",
                        "route", "/clubs",
                        "entityId", "7"
                )
        ));
        NotificationOutbox outbox = NotificationOutbox.create(payload, targetUserId);
        PushDevice targetDevice = PushDevice.create(
                targetUserId,
                "target-installation",
                "ExponentPushToken[target]",
                "android",
                "test-project",
                "1.0.0"
        );

        when(dispatchService.claimPendingOutboxes()).thenReturn(List.of(outbox));
        when(dispatchService.findActiveDevicesByUserId(targetUserId))
                .thenReturn(List.of(targetDevice));
        when(expoPushClient.sendMessages(anyList())).thenReturn(List.of());

        worker.processPendingNotifications();

        verify(dispatchService).findActiveDevicesByUserId(targetUserId);
        verify(dispatchService, never()).findAllActiveDevices();
        ArgumentCaptor<List<ExpoPushMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(expoPushClient).sendMessages(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getTo()).isEqualTo("ExponentPushToken[target]");
                    assertThat(message.getData())
                            .containsEntry("type", "club_recruitment")
                            .containsEntry("entityId", "7");
                });
        verify(dispatchService).markOutboxSent(null);
    }
}
