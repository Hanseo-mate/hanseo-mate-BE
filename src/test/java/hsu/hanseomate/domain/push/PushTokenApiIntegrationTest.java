package hsu.hanseomate.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PushTokenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PushDeviceRepository pushDeviceRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("DELETE FROM push_tickets");
        jdbcTemplate.execute("DELETE FROM push_devices");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    void anonymousRefreshKeepsAuthenticatedUserLinkUntilExplicitUnlink()
            throws Exception {
        long userId = createUser();
        String installationId = "same-installation";
        String expoPushToken = "ExponentPushToken[same-device]";

        registerToken(
                null,
                installationId,
                expoPushToken,
                "android",
                "1.0.0"
        );
        assertThat(findDevice(installationId).getUserId()).isNull();

        registerToken(
                userJwt(userId),
                installationId,
                expoPushToken,
                "ios",
                "1.1.0"
        );
        PushDevice linkedDevice = findDevice(installationId);
        assertThat(linkedDevice.getUserId()).isEqualTo(userId);
        assertThat(linkedDevice.getPlatform()).isEqualTo("ios");

        registerToken(
                null,
                installationId,
                expoPushToken,
                "android",
                "1.2.0"
        );
        PushDevice anonymouslyRefreshedDevice = findDevice(installationId);
        assertThat(anonymouslyRefreshedDevice.getUserId()).isEqualTo(userId);
        assertThat(anonymouslyRefreshedDevice.getPlatform()).isEqualTo("android");
        assertThat(anonymouslyRefreshedDevice.getAppVersion()).isEqualTo("1.2.0");

        mockMvc.perform(delete("/api/v1/push-tokens/{installationId}", installationId))
                .andExpect(status().isNoContent());

        assertThat(findDevice(installationId).getUserId()).isNull();
    }

    @Test
    void sameExpoTokenMovesToNewInstallationWithoutDuplicateKey()
            throws Exception {
        long userId = createUser();
        String expoPushToken = "ExponentPushToken[moved-device]";

        registerToken(
                userJwt(userId),
                "old-installation",
                expoPushToken,
                "android",
                "1.0.0"
        );
        Long originalDeviceId = findDevice("old-installation").getId();

        registerToken(
                null,
                "new-installation",
                expoPushToken,
                "ios",
                "2.0.0"
        );

        assertThat(pushDeviceRepository.count()).isEqualTo(1);
        assertThat(pushDeviceRepository.findByInstallationId("old-installation")).isEmpty();
        PushDevice movedDevice = findDevice("new-installation");
        assertThat(movedDevice.getId()).isEqualTo(originalDeviceId);
        assertThat(movedDevice.getUserId()).isNull();
        assertThat(movedDevice.getPlatform()).isEqualTo("ios");
        assertThat(movedDevice.getAppVersion()).isEqualTo("2.0.0");
    }

    @Test
    void installationAdoptsTokenOwnedByAnotherDeviceWithoutUniqueKeyFailure()
            throws Exception {
        registerToken(
                null,
                "installation-a",
                "ExponentPushToken[token-a]",
                "android",
                "1.0.0"
        );
        registerToken(
                null,
                "installation-b",
                "ExponentPushToken[token-b]",
                "ios",
                "1.0.0"
        );
        Long installationAId = findDevice("installation-a").getId();

        registerToken(
                null,
                "installation-a",
                "ExponentPushToken[token-b]",
                "ios",
                "2.0.0"
        );

        assertThat(pushDeviceRepository.count()).isEqualTo(1);
        assertThat(pushDeviceRepository.findByInstallationId("installation-b")).isEmpty();
        PushDevice survivingDevice = findDevice("installation-a");
        assertThat(survivingDevice.getId()).isEqualTo(installationAId);
        assertThat(survivingDevice.getExpoPushToken())
                .isEqualTo("ExponentPushToken[token-b]");
        assertThat(survivingDevice.getPlatform()).isEqualTo("ios");
        assertThat(survivingDevice.getAppVersion()).isEqualTo("2.0.0");
    }

    private void registerToken(
            RequestPostProcessor authentication,
            String installationId,
            String expoPushToken,
            String platform,
            String appVersion
    ) throws Exception {
        MockHttpServletRequestBuilder request = put("/api/v1/push-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "expoPushToken", expoPushToken,
                        "projectId", "test-project",
                        "platform", platform,
                        "installationId", installationId,
                        "appVersion", appVersion
                )));
        if (authentication != null) {
            request.with(authentication);
        }
        mockMvc.perform(request).andExpect(status().isOk());
    }

    private PushDevice findDevice(String installationId) {
        return pushDeviceRepository.findByInstallationId(installationId).orElseThrow();
    }

    private long createUser() {
        String loginId = "push-token-user-" + System.nanoTime();
        return userAccountRepository.saveAndFlush(
                UserAccount.create(loginId, "test-password-hash")
        ).getId();
    }

    private RequestPostProcessor userJwt(long userId) {
        return jwt().jwt(token -> token
                .subject(Long.toString(userId))
                .claim("role", "USER"));
    }
}
