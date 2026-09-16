package hsu.hanseomate.domain.appsetting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.appsetting.entity.AppFeatureSettingAudit;
import hsu.hanseomate.domain.appsetting.repository.AppFeatureSettingAuditRepository;
import hsu.hanseomate.domain.appsetting.repository.AppFeatureSettingRepository;
import hsu.hanseomate.domain.appsetting.service.FestivalFloatingButtonService;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import hsu.hanseomate.global.security.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FestivalFloatingButtonApiIntegrationTest {

    private static final String PATH = "/api/admin/settings/festival-floating-button";
    private static final Instant FIRST_CHANGE = Instant.parse("2026-09-05T04:00:00.123456Z");
    private static final Instant SECOND_CHANGE = Instant.parse("2026-09-05T04:10:00.654321Z");
    private static final String REQUEST_IP = "192.0.2.10";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private AppFeatureSettingRepository settingRepository;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtProperties jwtProperties;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private AppFeatureSettingAuditRepository auditRepository;

    private Long adminId;
    private Long userId;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        clearSettings();
        adminId = userRepository.saveAndFlush(UserAccount.create("festival-admin", "test-hash")).getId();
        userId = userRepository.saveAndFlush(UserAccount.create("festival-user", "test-hash")).getId();
        jdbcTemplate.update("UPDATE user_accounts SET role = 'ADMIN' WHERE id = ?", adminId);
        adminToken = token(adminId, "ADMIN", Instant.now().plusSeconds(3600));
        userToken = token(userId, "USER", Instant.now().plusSeconds(3600));
        when(clock.instant()).thenReturn(FIRST_CHANGE);
        when(clock.withZone(any())).thenAnswer(invocation -> Clock.fixed(clock.instant(), invocation.getArgument(0)));
    }

    @AfterEach
    void cleanUp() {
        clearSettings();
        userRepository.deleteById(adminId);
        userRepository.deleteById(userId);
    }

    @Test
    void missingSettingReturnsDefaultWithoutCreatingRow() throws Exception {
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.updatedAt").value(nullValue()));
        assertHomeVisibility(false);
        assertThat(settingRepository.count()).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void initialFalsePatchKeepsNullTimestampAndProducesNoAudit() throws Exception {
        update("{\"visible\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.updatedAt").value(nullValue()));
        update("{\"visible\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value(nullValue()));
        assertThat(settingRepository.count()).isEqualTo(1);
        assertThat(settingRepository.findById(FestivalFloatingButtonService.SETTING_KEY).orElseThrow().getUpdatedBy())
                .isNull();
        assertThat(auditCount()).isZero();
    }

    @Test
    void changesStateAndHomeImmediatelyWithAppendOnlyAudit() throws Exception {
        update("{\"visible\":true}")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.updatedAt").value(FIRST_CHANGE.toString()));
        assertHomeVisibility(true);

        when(clock.instant()).thenReturn(SECOND_CHANGE);
        update("{\"visible\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.updatedAt").value(SECOND_CHANGE.toString()));
        assertHomeVisibility(false);
        assertThat(auditCount()).isEqualTo(2);
        var changes = jdbcTemplate.queryForList(
                "SELECT changed_by, changed_at, previous_enabled, new_enabled, request_ip FROM app_feature_setting_audits ORDER BY id");
        assertThat(changes.get(0)).containsEntry("changed_by", adminId)
                .containsEntry("previous_enabled", false).containsEntry("new_enabled", true)
                .containsEntry("request_ip", REQUEST_IP);
        assertThat(changes.get(1)).containsEntry("changed_by", adminId)
                .containsEntry("previous_enabled", true).containsEntry("new_enabled", false);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT changed_at FROM app_feature_setting_audits ORDER BY id LIMIT 1", Instant.class))
                .isEqualTo(FIRST_CHANGE);
        assertThat(settingRepository.findById(FestivalFloatingButtonService.SETTING_KEY).orElseThrow().getUpdatedBy())
                .isEqualTo(adminId);
    }

    @Test
    void repeatedValueDoesNotChangeTimestampActorOrAudit() throws Exception {
        update("{\"visible\":true}").andExpect(status().isOk());
        when(clock.instant()).thenReturn(SECOND_CHANGE);
        jdbcTemplate.update("UPDATE user_accounts SET role = 'ADMIN' WHERE id = ?", userId);
        String otherAdminToken = token(userId, "ADMIN", Instant.now().plusSeconds(3600));
        mockMvc.perform(patch(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value(FIRST_CHANGE.toString()));
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.updatedAt").value(FIRST_CHANGE.toString()));
        assertThat(auditCount()).isEqualTo(1);
        assertThat(settingRepository.findById(FestivalFloatingButtonService.SETTING_KEY).orElseThrow().getUpdatedBy())
                .isEqualTo(adminId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"visible\":null}", "{\"visible\":\"true\"}", "{\"visible\":\"false\"}",
            "{\"visible\":1}", "{\"visible\":0}", "{\"visible\":[]}", "{\"visible\":{}}", "null", "[]", ""})
    void rejectsAnythingExceptJsonBoolean(String body) throws Exception {
        update(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString());
        assertThat(settingRepository.count()).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void auditFailureRollsBackAlreadyFlushedSettingChange() throws Exception {
        update("{\"visible\":true}").andExpect(status().isOk());
        doThrow(new DataIntegrityViolationException("simulated audit storage failure"))
                .when(auditRepository).saveAndFlush(any(AppFeatureSettingAudit.class));
        update("{\"visible\":false}").andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
        assertHomeVisibility(true);
        assertThat(settingRepository.findById(FestivalFloatingButtonService.SETTING_KEY).orElseThrow().getUpdatedAt())
                .isEqualTo(FIRST_CHANGE);
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void auditFailureAlsoRollsBackFirstSettingCreation() throws Exception {
        doThrow(new DataIntegrityViolationException("simulated audit storage failure"))
                .when(auditRepository).saveAndFlush(any(AppFeatureSettingAudit.class));
        update("{\"visible\":true}").andExpect(status().isInternalServerError());
        assertThat(settingRepository.count()).isZero();
        assertThat(auditCount()).isZero();
        assertHomeVisibility(false);
    }

    @Test
    void requiresAdminForBothEndpoints() throws Exception {
        for (String deniedToken : new String[]{"", "invalid-token", token(adminId, "ADMIN", Instant.now().minusSeconds(3600))}) {
            mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, deniedToken.isEmpty() ? "" : "Bearer " + deniedToken))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true}")
                            .header(HttpHeaders.AUTHORIZATION, deniedToken.isEmpty() ? "" : "Bearer " + deniedToken))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":true}"))
                .andExpect(status().isForbidden());
        assertThat(settingRepository.count()).isZero();
    }

    @Test
    void allowsAdminCorsPreflightAndRejectsNonJsonUpdate() throws Exception {
        mockMvc.perform(options(PATH).header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
        mockMvc.perform(patch(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.TEXT_PLAIN).content("{\"visible\":true}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void exposesRequiredBooleanAndNullableTimestampInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + PATH + "'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['" + PATH + "'].patch.responses['400']").exists())
                .andExpect(jsonPath("$.components.schemas.HomePageResponse.required").value(hasItem("festivalFloatingButtonVisible")))
                .andExpect(jsonPath("$.components.schemas.HomePageResponse.properties.festivalFloatingButtonVisible.type").value("boolean"))
                .andExpect(jsonPath("$.components.schemas.FestivalFloatingButtonUpdateRequest.required").value(hasItem("visible")))
                .andExpect(jsonPath("$.components.schemas.FestivalFloatingButtonUpdateRequest.properties.visible.type").value("boolean"))
                .andExpect(jsonPath("$.components.schemas.FestivalFloatingButtonResponse.required").value(hasItem("updatedAt")))
                .andExpect(jsonPath("$.components.schemas.FestivalFloatingButtonResponse.properties.updatedAt.type").value(hasItem("null")));
    }

    private ResultActions update(String body) throws Exception {
        return mockMvc.perform(patch(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body).with(request -> {
                    request.setRemoteAddr(REQUEST_IP);
                    request.addHeader("X-Forwarded-For", "203.0.113.99");
                    return request;
                }));
    }

    private void assertHomeVisibility(boolean visible) throws Exception {
        for (String accessToken : new String[]{"", userToken}) {
            var request = get("/api/home");
            if (!accessToken.isEmpty()) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            }
            mockMvc.perform(request).andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(jsonPath("$.festivalFloatingButtonVisible").value(visible))
                    .andExpect(jsonPath("$.festivalFloatingButtonVisible").isBoolean())
                    .andExpect(jsonPath("$.loggedIn").value(!accessToken.isEmpty()))
                    .andExpect(jsonPath("$.todayCourses").isArray())
                    .andExpect(jsonPath("$.popularNotices").isArray())
                    .andExpect(jsonPath("$.todayCafeteriaMenus").isArray());
        }
    }

    private String token(Long subject, String role, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuer(jwtProperties.issuer()).subject(subject.toString())
                .claim("role", role).issuedAt(expiresAt.minusSeconds(3600)).expiresAt(expiresAt).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private long auditCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_feature_setting_audits", Long.class);
    }

    private void clearSettings() {
        jdbcTemplate.update("DELETE FROM app_feature_setting_audits");
        settingRepository.deleteAllInBatch();
    }
}
