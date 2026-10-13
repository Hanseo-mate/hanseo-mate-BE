package hsu.hanseomate.domain.push;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.push.dto.NotificationPreferenceUpdateRequest;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.repository.NotificationPreferenceRepository;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.domain.push.service.NotificationPreferenceService;
import hsu.hanseomate.domain.push.type.NotificationCategory;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notification-preferences-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class NotificationPreferenceConcurrencyIntegrationTest {
    @Autowired NotificationPreferenceService service;
    @Autowired NotificationPreferenceRepository preferences;
    @Autowired PushDeviceRepository devices;

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
