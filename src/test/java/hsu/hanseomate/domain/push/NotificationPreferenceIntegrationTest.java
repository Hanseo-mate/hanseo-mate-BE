package hsu.hanseomate.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.notification.repository.NotificationRepository;
import hsu.hanseomate.domain.notification.service.NotificationInboxService;
import hsu.hanseomate.domain.push.client.ExpoPushClient;
import hsu.hanseomate.domain.push.client.ExpoPushMessage;
import hsu.hanseomate.domain.push.entity.NotificationOutbox;
import hsu.hanseomate.domain.push.entity.OutboxStatus;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.repository.NotificationOutboxRepository;
import hsu.hanseomate.domain.push.repository.NotificationPreferenceRepository;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.domain.push.service.NotificationDispatchService;
import hsu.hanseomate.domain.push.service.NotificationService;
import hsu.hanseomate.domain.push.service.PushTokenService;
import hsu.hanseomate.domain.push.dto.RegisterPushTokenRequest;
import hsu.hanseomate.domain.push.type.NotificationCategory;
import hsu.hanseomate.domain.push.worker.NotificationSendWorker;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notification-preferences;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationPreferenceIntegrationTest {
    private static final String API = "/api/v1/notification-preferences";
    private static final String OWNER = "owner-installation";
    private static final String GUEST = "guest-installation";
    private static final String OWNER_TOKEN = "ExponentPushToken[owner]";
    private static final String GUEST_TOKEN = "ExponentPushToken[guest]";

    @Autowired MockMvc mvc;
    @Autowired PushDeviceRepository devices;
    @Autowired NotificationPreferenceRepository preferences;
    @Autowired UserAccountRepository users;
    @Autowired NotificationOutboxRepository outboxes;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationService notificationService;
    @Autowired NotificationDispatchService dispatch;
    @Autowired NotificationInboxService inbox;
    @Autowired PushTokenService pushTokens;
    @MockitoBean ExpoPushClient expo;
    private long userId;
    private NotificationSendWorker worker;

    @BeforeEach
    void setup() {
        userId = users.saveAndFlush(UserAccount.create("prefs-" + UUID.randomUUID(), "hash")).getId();
        devices.saveAndFlush(PushDevice.create(userId, OWNER, OWNER_TOKEN, "android", "project", "1.0.0"));
        devices.saveAndFlush(PushDevice.create(null, GUEST, GUEST_TOKEN, "ios", "project", "1.0.0"));
        PushDevice inactive = PushDevice.create(userId, "inactive", "ExponentPushToken[inactive]", "android", "project", "1.0.0");
        inactive.deactivate("DeviceNotRegistered");
        devices.saveAndFlush(inactive);
        when(expo.sendMessages(anyList())).thenReturn(List.of());
        worker = new NotificationSendWorker(dispatch, expo, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-14T08:50:30Z"), ZoneOffset.UTC));
    }

    @Test
    void defaultsAreAllOnForLoggedInAndAnonymousWithoutCreatingSettings() throws Exception {
        for (String installationId : List.of(OWNER, GUEST)) {
            var response = mvc.perform(get(API).param("installationId", installationId)
                    .header("X-Push-Token", token(installationId)))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.enabled").value(true));
            for (NotificationCategory category : NotificationCategory.values()) {
                response.andExpect(jsonPath("$.categories." + category.name()).value(true));
            }
        }
        assertThat(preferences.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void initialRegistrationStoresLocalMasterOffForGuestsAndUsers(boolean loggedIn) throws Exception {
        String id = "initial-installation";
        String pushToken = "ExponentPushToken[initial]";
        var request = put("/api/v1/push-tokens").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(Map.of(
                        "installationId", id, "expoPushToken", pushToken, "platform", "android",
                        "projectId", "project", "appVersion", "1.0.0",
                        "notificationPreferences", Map.of("enabled", false))));
        if (loggedIn) request.with(jwt().jwt(value -> value.subject(Long.toString(userId)).claim("role", "USER")));
        mvc.perform(request).andExpect(status().isOk());
        assertThat(devices.findByInstallationId(id).orElseThrow().getUserId()).isEqualTo(loggedIn ? userId : null);
        mvc.perform(get(API).param("installationId", id).header("X-Push-Token", pushToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        enqueueAll();
        worker.processPendingNotifications();
        var batches = ArgumentCaptor.forClass(List.class);
        verify(expo, org.mockito.Mockito.atLeastOnce()).sendMessages(batches.capture());
        for (List<?> batch : batches.getAllValues()) {
            assertThat(batch).allSatisfy(message -> assertThat(((ExpoPushMessage) message).getTo()).isNotEqualTo(pushToken));
        }
    }

    @ParameterizedTest
    @EnumSource(NotificationCategory.class)
    void initialRegistrationAppliesEachLocalCategoryOffWithoutSeparatePatch(NotificationCategory disabled) throws Exception {
        String id = "initial-category-installation";
        String pushToken = "ExponentPushToken[initial-category]";
        mvc.perform(put("/api/v1/push-tokens")
                .with(jwt().jwt(value -> value.subject(Long.toString(userId)).claim("role", "USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(Map.of(
                        "installationId", id, "expoPushToken", pushToken, "platform", "android",
                        "projectId", "project", "appVersion", "1.0.0",
                        "notificationPreferences", Map.of("categories", Map.of(disabled.name(), false))))))
                .andExpect(status().isOk());
        enqueueAll();
        worker.processPendingNotifications();
        var batches = ArgumentCaptor.forClass(List.class);
        verify(expo, org.mockito.Mockito.atLeastOnce()).sendMessages(batches.capture());
        @SuppressWarnings("unchecked")
        List<ExpoPushMessage> sentToNewDevice = batches.getAllValues().stream()
                .flatMap(batch -> ((List<ExpoPushMessage>) batch).stream())
                .filter(message -> pushToken.equals(message.getTo())).toList();
        assertThat(sentToNewDevice).hasSize(5)
                .noneMatch(message -> disabled.name().equals(message.getData().get("category")));
    }

    @Test
    void invalidInitialSettingsDoNotRegisterDeviceOrRotateExistingToken() throws Exception {
        for (String id : List.of("invalid-new-installation", OWNER)) {
            mvc.perform(put("/api/v1/push-tokens").contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(Map.of(
                            "installationId", id, "expoPushToken", "ExponentPushToken[invalid]", "platform", "android",
                            "projectId", "project", "appVersion", "1.0.0",
                            "notificationPreferences", Map.of("enabled", false, "categories", Map.of("UNKNOWN", false))))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(devices.findByInstallationId("invalid-new-installation")).isEmpty();
        assertThat(devices.findByInstallationId(OWNER).orElseThrow().getExpoPushToken()).isEqualTo(OWNER_TOKEN);
        assertThat(preferences.count()).isZero();
    }

    @Test
    void masterSwitchPreservesPartialChoicesAndDevicesAreIndependent() throws Exception {
        update(OWNER, "{\"categories\":{\"CLASS_REMINDER\":false}}");
        update(OWNER, "{\"enabled\":false}");
        update(OWNER, "{\"enabled\":true}");
        mvc.perform(get(API).param("installationId", OWNER).header("X-Push-Token", OWNER_TOKEN))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.categories.CLASS_REMINDER").value(false))
                .andExpect(jsonPath("$.categories.SHUTTLE_RESERVATION").value(true));
        mvc.perform(get(API).param("installationId", GUEST).header("X-Push-Token", GUEST_TOKEN))
                .andExpect(jsonPath("$.categories.CLASS_REMINDER").value(true));
    }

    @ParameterizedTest
    @EnumSource(NotificationCategory.class)
    void switchesAreAppliedAfterEnqueueForAllSixKindsAndRetainOriginalRecipients(NotificationCategory disabled)
            throws Exception {
        enqueueAll();
        update(OWNER, "{\"categories\":{\"" + disabled.name() + "\":false}}");
        update(GUEST, "{\"categories\":{\"" + disabled.name() + "\":false}}");

        worker.processPendingNotifications();

        var batches = ArgumentCaptor.forClass(List.class);
        verify(expo, org.mockito.Mockito.atLeastOnce()).sendMessages(batches.capture());
        @SuppressWarnings("unchecked")
        List<ExpoPushMessage> sent = batches.getAllValues().stream()
                .flatMap(batch -> ((List<ExpoPushMessage>) batch).stream()).toList();
        assertThat(sent).noneMatch(message -> disabled.name().equals(message.getData().get("category")));
        assertThat(sent.stream().filter(message -> OWNER_TOKEN.equals(message.getTo()))).hasSize(5);
        int guestExpected = isPersonal(disabled) ? 4 : 3;
        assertThat(sent.stream().filter(message -> GUEST_TOKEN.equals(message.getTo()))).hasSize(guestExpected);
        assertThat(sent).noneMatch(message -> "ExponentPushToken[inactive]".equals(message.getTo()));
        assertThat(sent.stream().filter(message -> isPersonal(NotificationCategory.valueOf(
                message.getData().get("category").toString())))).allSatisfy(message ->
                assertThat(message.getTo()).isEqualTo(OWNER_TOKEN));
        assertThat(outboxes.findAll()).allSatisfy(outbox -> assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT));
        assertThat(notifications.count()).isEqualTo(6);
        assertThat(inbox.getUnreadCount(OWNER).unreadCount()).isEqualTo(6);
        assertThat(inbox.getUnreadCount(GUEST).unreadCount()).isEqualTo(4);

        update(OWNER, "{\"categories\":{\"" + disabled.name() + "\":true}}");
        update(GUEST, "{\"categories\":{\"" + disabled.name() + "\":true}}");
        clearInvocations(expo);
        worker.processPendingNotifications();
        verify(expo, never()).sendMessages(anyList());
        enqueue(disabled);
        worker.processPendingNotifications();
        verify(expo).sendMessages(anyList());
    }

    @Test
    void masterOffStopsAllPushIncludingTestWithoutRemovingInboxRecords() throws Exception {
        enqueueAll();
        notificationService.enqueueTestNotification("테스트", "내용");
        update(OWNER, "{\"enabled\":false}");
        update(GUEST, "{\"enabled\":false}");
        worker.processPendingNotifications();
        verify(expo, never()).sendMessages(anyList());
        assertThat(notifications.count()).isEqualTo(7);
        assertThat(inbox.getNotifications(OWNER, 0, 10)).hasSize(7);
        assertThat(inbox.getNotifications(GUEST, 0, 10)).hasSize(5);
        assertThat(outboxes.findAll()).allSatisfy(outbox -> assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT));
    }

    @Test
    void legacyStudentCouncilPayloadUsesSeparateSwitch() throws Exception {
        String oldPayload = "{\"title\":\"[학생회 공지] 예전 공지\",\"body\":\"내용\",\"data\":{\"type\":\"notice\"}}";
        outboxes.save(NotificationOutbox.create(oldPayload));
        update(OWNER, "{\"categories\":{\"STUDENT_COUNCIL_NOTICE\":false}}");
        update(GUEST, "{\"categories\":{\"STUDENT_COUNCIL_NOTICE\":false}}");
        worker.processPendingNotifications();
        verify(expo, never()).sendMessages(anyList());
    }

    @Test
    void loginLogoutAndTokenRotationPreserveSettingsButNewInstallationDefaultsOn() throws Exception {
        update(GUEST, "{\"enabled\":false,\"categories\":{\"SHUTTLE_RESERVATION\":false}}");
        String rotated = "ExponentPushToken[rotated]";
        pushTokens.registerOrUpdateToken(userId, registration(GUEST, rotated));
        mvc.perform(get(API).param("installationId", GUEST).header("X-Push-Token", rotated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        pushTokens.unlinkUser(GUEST);
        pushTokens.registerOrUpdateToken(null, registration(GUEST, rotated));
        mvc.perform(get(API).param("installationId", GUEST).header("X-Push-Token", rotated))
                .andExpect(jsonPath("$.categories.SHUTTLE_RESERVATION").value(false));
        mvc.perform(get(API).param("installationId", GUEST).header("X-Push-Token", GUEST_TOKEN))
                .andExpect(status().isForbidden());
        pushTokens.registerOrUpdateToken(null, registration("new-installation", rotated));
        mvc.perform(get(API).param("installationId", "new-installation").header("X-Push-Token", rotated))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        assertThat(devices.findByInstallationId("new-installation").orElseThrow().getUserId()).isNull();
    }

    @Test
    void requiresRegisteredInstallationAndCurrentMatchingToken() throws Exception {
        mvc.perform(get(API).param("installationId", OWNER)).andExpect(status().isBadRequest());
        mvc.perform(get(API).header("X-Push-Token", OWNER_TOKEN)).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("installationId", "missing").header("X-Push-Token", OWNER_TOKEN))
                .andExpect(status().isForbidden());
        mvc.perform(patch(API).param("installationId", OWNER).header("X-Push-Token", GUEST_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        assertThat(preferences.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"enabled\":null}", "{\"enabled\":\"false\"}", "{\"enabled\":0}",
            "{\"categories\":null}", "{\"categories\":{}}", "{\"enabled\":false,\"unknown\":true}",
            "{\"enabled\":false,\"categories\":{\"UNKNOWN\":false}}", "{\"categories\":{\"CLASS_REMINDER\":null}}",
            "{\"categories\":{\"CLASS_REMINDER\":\"false\"}}"})
    void invalidPatchDoesNotSaveAnything(String body) throws Exception {
        mvc.perform(patch(API).param("installationId", OWNER).header("X-Push-Token", OWNER_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertThat(preferences.count()).isZero();
    }

    @Test
    void allowsCorsPreflightForDeviceProofHeader() throws Exception {
        mvc.perform(options(API).header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "content-type,x-push-token"))
                .andExpect(status().isOk()).andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    private void update(String installationId, String body) throws Exception {
        mvc.perform(patch(API).param("installationId", installationId).header("X-Push-Token", token(installationId))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private String token(String installationId) {
        return OWNER.equals(installationId) ? OWNER_TOKEN : GUEST_TOKEN;
    }

    private RegisterPushTokenRequest registration(String id, String token) {
        return new RegisterPushTokenRequest(token, "project", "android", id, "1.0.0");
    }

    private boolean isPersonal(NotificationCategory category) {
        return category == NotificationCategory.CLUB_RECRUITMENT || category == NotificationCategory.CLASS_REMINDER;
    }

    private void enqueueAll() {
        for (NotificationCategory category : NotificationCategory.values()) enqueue(category);
    }

    private void enqueue(NotificationCategory category) {
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-10-14T18:00:00+09:00");
        switch (category) {
            case NOTICE_MILESTONE -> notificationService.enqueueNoticeNotification("공지", "내용", "10");
            case STUDENT_COUNCIL_NOTICE -> notificationService.enqueueStudentCouncilNoticeNotification("[학생회 공지] 새 공지", "내용", "10");
            case SYSTEM_NOTICE -> notificationService.enqueueSystemNoticeNotification("시스템", "내용", "10");
            case CLUB_RECRUITMENT -> notificationService.enqueueClubRecruitmentNotification("동아리", "내용", "10", List.of(userId));
            case CLASS_REMINDER -> notificationService.enqueueTimetableClassReminder(userId, 10L, 11L, 2026, 2, "수업", startsAt.plusHours(1));
            case SHUTTLE_RESERVATION -> notificationService.enqueueShuttleReservationReminder(startsAt);
        }
    }
}
