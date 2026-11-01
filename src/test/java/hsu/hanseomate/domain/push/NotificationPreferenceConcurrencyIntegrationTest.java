package hsu.hanseomate.domain.push;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.push.dto.NotificationPreferenceUpdateRequest;
import hsu.hanseomate.domain.push.dto.RegisterPushTokenRequest;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.repository.NotificationPreferenceRepository;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.domain.push.service.NotificationPreferenceService;
import hsu.hanseomate.domain.push.service.NotificationDispatchService;
import hsu.hanseomate.domain.push.service.PushTokenService;
import hsu.hanseomate.domain.push.type.NotificationCategory;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notification-preferences-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class NotificationPreferenceConcurrencyIntegrationTest {
    @Autowired NotificationPreferenceService service;
    @Autowired NotificationPreferenceRepository preferences;
    @Autowired PushDeviceRepository devices;
    @Autowired PushTokenService pushTokens;
    @Autowired NotificationDispatchService dispatch;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void newDeviceBecomesVisibleOnlyWithItsLocalOffSettings() throws Exception {
        String installationId = "atomic-installation";
        String token = "ExponentPushToken[atomic]";
        var executor = Executors.newSingleThreadExecutor();
        var written = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        try {
            var registration = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                pushTokens.registerOrUpdateToken(null, new RegisterPushTokenRequest(token, "project", "android",
                        installationId, "1.0.0", Map.of("enabled", false)));
                preferences.flush();
                written.countDown();
                try {
                    if (!allowCommit.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting to commit");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(written.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatch.findAllActiveDevices()).noneMatch(device -> installationId.equals(device.getInstallationId()));
            allowCommit.countDown();
            registration.get(15, TimeUnit.SECONDS);
            var registered = devices.findByInstallationId(installationId).orElseThrow();
            assertThat(service.get(installationId, token).enabled()).isFalse();
            assertThat(dispatch.filterEnabledDevices(java.util.List.of(registered), NotificationCategory.SHUTTLE_RESERVATION)).isEmpty();
        } finally {
            allowCommit.countDown();
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
            preferences.findById(installationId).ifPresent(preferences::delete);
            devices.findByInstallationId(installationId).ifPresent(devices::delete);
        }
    }

    @Test
    void concurrentInitialPatchesKeepBothChoicesInOneRow() throws Exception {
        String installationId = "concurrent-installation";
        String token = "ExponentPushToken[concurrent]";
        PushDevice device = devices.saveAndFlush(PushDevice.create(null, installationId, token, "android", "project", "1.0.0"));
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                start.await();
                return service.update(installationId, token, NotificationPreferenceUpdateRequest.parse(
                        Map.of("categories", Map.of("CLASS_REMINDER", false))));
            });
            var second = executor.submit(() -> {
                start.await();
                return service.update(installationId, token, NotificationPreferenceUpdateRequest.parse(
                        Map.of("categories", Map.of("SHUTTLE_RESERVATION", false))));
            });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertThat(preferences.count()).isEqualTo(1);
            var result = service.get(installationId, token);
            assertThat(result.enabled()).isTrue();
            assertThat(result.categories()).containsEntry(NotificationCategory.CLASS_REMINDER, false)
                    .containsEntry(NotificationCategory.SHUTTLE_RESERVATION, false);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(15, TimeUnit.SECONDS);
            preferences.deleteById(installationId);
            devices.deleteById(device.getId());
        }
    }
}
