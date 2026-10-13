package hsu.hanseomate.domain.push.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationSendWorkerTest {

    @Test
    void shuttleReminderIsSentToBothLoggedInAndAnonymousActiveDevices() throws Exception {
        NotificationDispatchService dispatchService = mock(NotificationDispatchService.class);
        ExpoPushClient expoPushClient = mock(ExpoPushClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationSendWorker worker = new NotificationSendWorker(
                dispatchService, expoPushClient, objectMapper,
                Clock.fixed(Instant.parse("2026-10-14T08:50:30Z"), ZoneOffset.UTC));
        String payload = objectMapper.writeValueAsString(new NotificationPayload(
                "[셔틀 예매 알림]", "약 10분 뒤인 오후 6시에 셔틀 예매가 시작됩니다.",
                Map.of("version", 1, "type", "shuttle_reservation", "route", "/notifications")));
        NotificationOutbox outbox = NotificationOutbox.create(payload, null,
                LocalDateTime.of(2026, 10, 14, 9, 0));
        PushDevice loggedIn = PushDevice.create(42L, "logged-in", "ExponentPushToken[logged-in]",
                "android", "test-project", "1.0.0");
        PushDevice anonymous = PushDevice.create(null, "guest", "ExponentPushToken[guest]",
                "ios", "test-project", "1.0.0");
        when(dispatchService.claimPendingOutboxes()).thenReturn(List.of(outbox));
        when(dispatchService.findAllActiveDevices()).thenReturn(List.of(loggedIn, anonymous));
        when(dispatchService.filterEnabledDevices(anyList(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(expoPushClient.sendMessages(anyList())).thenReturn(List.of());

        worker.processPendingNotifications();

        ArgumentCaptor<List<ExpoPushMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(expoPushClient).sendMessages(messages.capture());
        assertThat(messages.getValue()).extracting(ExpoPushMessage::getTo)
                .containsExactly("ExponentPushToken[logged-in]", "ExponentPushToken[guest]");
        assertThat(messages.getValue()).allSatisfy(message ->
                assertThat(message.getData()).containsEntry("type", "shuttle_reservation"));
        verify(dispatchService, never()).findActiveDevicesByUserId(anyLong());
        verify(dispatchService).markOutboxSent(null);
    }

    @Test
    void shuttleReminderIsNotSentOnceReservationsHaveOpened() {
        NotificationDispatchService dispatchService = mock(NotificationDispatchService.class);
        ExpoPushClient expoPushClient = mock(ExpoPushClient.class);
        NotificationSendWorker worker = new NotificationSendWorker(
                dispatchService, expoPushClient, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-14T09:00:00Z"), ZoneOffset.UTC));
        NotificationOutbox outbox = NotificationOutbox.create("{}", null,
                LocalDateTime.of(2026, 10, 14, 9, 0));
        when(dispatchService.claimPendingOutboxes()).thenReturn(List.of(outbox));

        worker.processPendingNotifications();

        verify(dispatchService).markOutboxExpired(null);
        verify(dispatchService, never()).findAllActiveDevices();
        verify(expoPushClient, never()).sendMessages(anyList());
    }

    @Test
    void targetedOutboxIsSentOnlyToActiveDevicesBelongingToTargetUser() throws Exception {
        NotificationDispatchService dispatchService = mock(NotificationDispatchService.class);
        ExpoPushClient expoPushClient = mock(ExpoPushClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationSendWorker worker = new NotificationSendWorker(
                dispatchService,
                expoPushClient,
                objectMapper,
                Clock.systemUTC()
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
        when(dispatchService.filterEnabledDevices(anyList(), any())).thenAnswer(invocation -> invocation.getArgument(0));
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
