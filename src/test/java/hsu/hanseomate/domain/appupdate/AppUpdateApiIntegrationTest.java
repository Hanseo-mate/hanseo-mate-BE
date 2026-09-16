package hsu.hanseomate.domain.appupdate;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hsu.hanseomate.domain.appupdate.config.AppUpdateProperties;
import hsu.hanseomate.domain.appupdate.entity.AppUpdateAudit;
import hsu.hanseomate.domain.appupdate.repository.*;
import hsu.hanseomate.domain.appupdate.service.AppUpdatePolicyOperations;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"app.updates.ios-app-store-id=1234567890", "app.updates.check-requests-per-minute=100000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppUpdateApiIntegrationTest {
    private static final String ADMIN = "/api/admin/app-update-policies";
    private static final String CHECK = "/api/app-updates/check";
    private static final String REASON = "스토어 배포 확인 후 정책 변경";
    private static final Instant NOW = Instant.parse("2026-09-09T04:00:00.123456Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUpdatePolicyOperations operations;
    @Autowired AppUpdatePolicyRepository policies;
    @Autowired AppUpdateCommandRepository commands;
    @Autowired AppUpdateProperties properties;
    @MockitoBean Clock clock;
    @MockitoSpyBean AppUpdateAuditRepository audits;

    @BeforeEach
    void before() {
        when(clock.instant()).thenReturn(NOW);
        jdbc.update("DELETE FROM app_update_audits");
        jdbc.update("DELETE FROM app_update_commands");
        jdbc.update("DELETE FROM app_update_policies");
        jdbc.update("DELETE FROM app_update_platform_locks");
        properties.setIosAppStoreId("1234567890");
    }

    @Test
    void anonymousCheckNeedsNoPolicyOrJwtAndDoesNotSeedRows() throws Exception {
        check("IOS", 28).andExpect(status().isOk()).andExpect(jsonPath("$.action").value("NONE"))
                .andExpect(jsonPath("$.policy").value(nullValue()))
                .andExpect(jsonPath("$.checkedAt").value(NOW.toString()))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(CHECK).param("platform", "ANDROID").param("build", "1").param("version", "1.10")
                        .header("Authorization", "Bearer expired-or-invalid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("NONE"));
        assertThat(policies.count()).isZero();
        assertThat(audits.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"1,REQUIRED", "27,REQUIRED", "28,OPTIONAL", "29,OPTIONAL", "30,NONE", "31,NONE", "9223372036854775807,NONE"})
    void numericBuildBoundaryMatchesPreview(long build, String expected) throws Exception {
        JsonNode draft = create("IOS", true, 28L, true);
        long id = draft.path("id").asLong();
        admin(post(ADMIN + "/" + id + "/preview").content(json(Map.of("revision", 1, "builds", List.of(build)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].action").value(expected));
        publish(draft, "IMMEDIATE", null, UUID.randomUUID().toString()).andExpect(status().isOk());
        check("IOS", build).andExpect(status().isOk()).andExpect(jsonPath("$.action").value(expected))
                .andExpect(jsonPath("$.policy.platform").value("IOS"))
                .andExpect(jsonPath("$.policy.optionalUpdateEnabled").doesNotExist())
                .andExpect(jsonPath("$.policy.createdBy").doesNotExist());
    }

    @Test
    void platformsAndOptionalSwitchAreIndependent() throws Exception {
        publish(create("IOS", true, 28L, false), "IMMEDIATE", null, "ios").andExpect(status().isOk());
        check("ANDROID", 1).andExpect(jsonPath("$.policy").value(nullValue()));
        publish(create("ANDROID", false, null, true), "IMMEDIATE", null, "android").andExpect(status().isOk());
        check("IOS", 29).andExpect(jsonPath("$.action").value("NONE"));
        check("ANDROID", 1).andExpect(jsonPath("$.action").value("OPTIONAL"))
                .andExpect(jsonPath("$.policy.minimumSupportedBuild").value(nullValue()))
                .andExpect(jsonPath("$.policy.storeUrl").value("https://play.google.com/store/apps/details?id=com.hanseomate.app"));
        publish(create("ANDROID", false, null, false), "IMMEDIATE", null, "android-disabled")
                .andExpect(status().isOk());
        check("ANDROID", 1).andExpect(jsonPath("$.action").value("NONE"));
    }

    @ParameterizedTest
    @CsvSource({"platform,WINDOWS", "platform,ios", "platform,''", "build,0", "build,-1",
            "build,1.2", "build,abc", "build,0x1", "build,1e2", "build,9223372036854775808", "version,''"})
    void malformedPublicParametersAre400(String field, String value) throws Exception {
        Map<String, String> params = new HashMap<>(Map.of("platform", "IOS", "build", "1", "version", "1.2"));
        params.put(field, value);
        MockHttpServletRequestBuilder request = get(CHECK);
        for (var entry : params.entrySet()) request.param(entry.getKey(), entry.getValue());
        mvc.perform(request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.path").value(CHECK))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"platform", "build", "version"})
    void missingPublicParametersAre400(String field) throws Exception {
        MockHttpServletRequestBuilder request = get(CHECK);
        for (var entry : Map.of("platform", "IOS", "build", "1", "version", "1.2").entrySet()) {
            if (!entry.getKey().equals(field)) request.param(entry.getKey(), entry.getValue());
        }
        mvc.perform(request).andExpect(status().isBadRequest());
    }

    @Test
    void publicVersionIsOnlyDisplayText() throws Exception {
        publish(create("IOS", true, 28L, true), "IMMEDIATE", null, "display").andExpect(status().isOk());
        for (String version : List.of("1.2", "1.10", "9.999", "beta")) {
            mvc.perform(get(CHECK).param("platform", "IOS").param("build", "27").param("version", version))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("REQUIRED"));
        }
        mvc.perform(get(CHECK).param("platform", "IOS").param("build", "27").param("version", "x".repeat(33)))
                .andExpect(status().isBadRequest());
        mvc.perform(get(CHECK).param("platform", "IOS").param("build", "27").param("version", "a\nb"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"latestBuild\":0", "\"latestBuild\":1.2", "\"latestBuild\":\"30\"",
            "\"latestBuild\":null", "\"forceUpdateEnabled\":\"true\"", "\"forceUpdateEnabled\":1",
            "\"forceUpdateEnabled\":null", "\"optionalUpdateEnabled\":\"false\"",
            "\"optionalUpdateEnabled\":null", "\"minimumSupportedBuild\":31", "\"minimumSupportedBuild\":null",
            "\"minimumSupportedBuild\":0", "\"minimumSupportedBuild\":27.5", "\"minimumSupportedBuild\":\"28\"",
            "\"latestVersion\":123", "\"latestVersion\":\"\"", "\"title\":\" \"", "\"title\":\"a\"",
            "\"title\":\"<script>alert(1)</script>\"", "\"message\":\"hello\\nworld\"", "\"message\":\"\"",
            "\"reason\":\"짧음\"", "\"platform\":\"WINDOWS\"", "\"platform\":1", "\"platform\":\"0\""
    })
    void rejectsInvalidStoredFieldsEvenWhenFrontendValidationIsBypassed(String replacement) throws Exception {
        var payload = mapper.readTree(json(content("IOS", true, 28L, true))).deepCopy();
        var entry = mapper.readTree("{" + replacement + "}").properties().iterator().next();
        ((tools.jackson.databind.node.ObjectNode) payload).set(entry.getKey(), entry.getValue());
        admin(post(ADMIN).content(json(payload))).andExpect(status().isBadRequest());
        assertThat(policies.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "revision", "createdBy", "updatedBy", "publishedBy", "effectiveAt"})
    void rejectsServerManagedFieldsInCreate(String field) throws Exception {
        Map<String, Object> body = content("IOS", true, 28L, true);
        body.put(field, "ACTIVE");
        admin(post(ADMIN).content(json(body))).andExpect(status().isBadRequest());
        assertThat(policies.count()).isZero();
    }

    @Test
    void rejectsDisabledForceWithMinimumAndTrimsPlainText() throws Exception {
        admin(post(ADMIN).content(json(content("IOS", false, 28L, true)))).andExpect(status().isBadRequest());
        Map<String, Object> body = content("IOS", false, null, true);
        body.put("title", "  업데이트가 필요해요  ");
        body.put("reason", "          x          ");
        admin(post(ADMIN).content(json(body))).andExpect(status().isBadRequest());
        body.put("reason", REASON);
        admin(post(ADMIN).content(json(body))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("업데이트가 필요해요"))
                .andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://apps.apple.com/app/id1234567890", "https://apps.apple.com.evil.test/app/id1234567890",
            "https://evil.test@apps.apple.com/app/id1234567890", "https://apps.apple.com/app/id999",
            "https://apps.apple.com/app/id12345678900", "https://apps.apple.com:8443/app/id1234567890",
            "javascript:alert(1)", "data:text/plain,hello", "file:///app", "intent://app",
            "https://play.google.com/store/apps/details?id=com.hanseomate.app"
    })
    void rejectsUnsafeOrWrongIosStoreUrls(String url) throws Exception {
        Map<String, Object> body = content("IOS", false, null, true);
        body.put("storeUrl", url);
        admin(post(ADMIN).content(json(body))).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://play.google.com/store/apps/details?id=com.other.app",
            "https://play.google.com/store/apps/details?id=com.hanseomate.app&id=com.other.app",
            "https://play.google.com/store/apps/details?%69d=com.hanseomate.app&id=com.hanseomate.app",
            "https://play.google.com/store/apps/details", "https://play.google.com/other?id=com.hanseomate.app",
            "https://play.google.com/store/apps/details?id=com.hanseomate.app#other"
    })
    void rejectsWrongAndroidPackageAndAmbiguousIds(String url) throws Exception {
        Map<String, Object> body = content("ANDROID", false, null, true);
        body.put("storeUrl", url);
        admin(post(ADMIN).content(json(body))).andExpect(status().isBadRequest());
    }

    @Test
    void iosConfigurationIsRequiredOnlyForIosWrites() throws Exception {
        properties.setIosAppStoreId("");
        admin(post(ADMIN).content(json(content("IOS", false, null, true)))).andExpect(status().isBadRequest());
        create("ANDROID", false, null, true);
        check("IOS", 1).andExpect(status().isOk()).andExpect(jsonPath("$.action").value("NONE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://apps.apple.com/app/id1234567890",
            "https://apps.apple.com/kr/app/id1234567890",
            "https://apps.apple.com/kr/app/hanseomate/id1234567890"
    })
    void acceptsSupportedAppleCountryAndSlugPaths(String url) throws Exception {
        Map<String, Object> body = content("IOS", false, null, true);
        body.put("storeUrl", url);
        admin(post(ADMIN).content(json(body))).andExpect(status().isCreated());
    }

    @Test
    void updateRequiresDraftAndCurrentRevisionAndDoesNotChangePlatform() throws Exception {
        JsonNode draft = create("IOS", true, 28L, true);
        long id = draft.path("id").asLong();
        Map<String, Object> update = content("IOS", true, 29L, true);
        update.remove("platform");
        update.put("revision", 1);
        admin(put(ADMIN + "/" + id).content(json(update))).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2)).andExpect(jsonPath("$.minimumSupportedBuild").value(29));
        admin(put(ADMIN + "/" + id).content(json(update))).andExpect(status().isConflict());
        update.put("revision", 2);
        update.put("platform", "ANDROID");
        admin(put(ADMIN + "/" + id).content(json(update))).andExpect(status().isBadRequest());
        update.remove("platform");
        draft = getPolicy(id);
        publish(draft, "IMMEDIATE", null, "edit").andExpect(status().isOk());
        update.put("revision", 3);
        admin(put(ADMIN + "/" + id).content(json(update))).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @CsvSource({"IMMEDIATE,2026-09-09T05:00:00Z", "SCHEDULED,null", "SCHEDULED,2026-09-09T04:00:00.123456Z",
            "SCHEDULED,2026-09-08T00:00:00Z", "SCHEDULED,2026-09-10T13:00:00+09:00",
            "SCHEDULED,+10000-01-01T00:00:00Z", "0,null"})
    void rejectsInvalidPublishTime(String mode, String date) throws Exception {
        JsonNode draft = create("IOS", true, 28L, true);
        publish(draft, mode, date.equals("null") ? null : date, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest());
        assertThat(commands.count()).isZero();
        assertThat(getPolicy(draft.path("id").asLong()).path("status").asString()).isEqualTo("DRAFT");
    }

    @Test
    void storeConfirmationAndIdempotencyKeyAreRequired() throws Exception {
        JsonNode draft = create("IOS", true, 28L, true);
        Map<String, Object> body = publishBody(draft, "IMMEDIATE", null);
        body.put("storeAvailabilityConfirmed", false);
        admin(post(ADMIN + "/" + draft.path("id").asLong() + "/publish")
                .header("Idempotency-Key", "unconfirmed").content(json(body))).andExpect(status().isBadRequest());
        body.put("storeAvailabilityConfirmed", true);
        admin(post(ADMIN + "/" + draft.path("id").asLong() + "/publish").content(json(body)))
                .andExpect(status().isBadRequest());
        check("IOS", 1).andExpect(jsonPath("$.action").value("NONE"));
    }

    @Test
    void duplicatePublishReturnsOriginalResultEvenAfterLaterSupersession() throws Exception {
        JsonNode draft = create("IOS", true, 28L, true);
        JsonNode first = tree(publish(draft, "IMMEDIATE", null, "same-key").andExpect(status().isOk()));
        publish(create("IOS", true, 29L, true), "IMMEDIATE", null, "second-policy").andExpect(status().isOk());
        JsonNode replay = tree(publish(draft, "IMMEDIATE", null, "same-key").andExpect(status().isOk()));
        assertThat(replay).isEqualTo(first);
        assertThat(getPolicy(draft.path("id").asLong()).path("status").asString()).isEqualTo("SUPERSEDED");
        Map<String, Object> changed = publishBody(draft, "IMMEDIATE", null);
        changed.put("reason", "동일한 키를 다른 요청에 재사용");
        admin(post(ADMIN + "/" + draft.path("id").asLong() + "/publish")
                .header("Idempotency-Key", "same-key").content(json(changed))).andExpect(status().isConflict());
        assertThat(commands.count()).isEqualTo(2);
        assertThat(count("event_type = 'PUBLISH' AND success = true")).isEqualTo(2);
    }

    @Test
    void scheduleActivatesOnlyWhenDueAndCanBeRetriedAcrossWorkers() throws Exception {
        JsonNode old = tree(publish(create("IOS", true, 27L, true), "IMMEDIATE", null, "initial").andExpect(status().isOk()));
        JsonNode draft = create("IOS", true, 29L, true);
        JsonNode scheduled = tree(publish(draft, "SCHEDULED", NOW.plusSeconds(10).toString(), "scheduled").andExpect(status().isOk()));
        long id = scheduled.path("id").asLong();
        assertThat(operations.activateDue(id)).isFalse();
        check("IOS", 28).andExpect(jsonPath("$.action").value("OPTIONAL"));
        when(clock.instant()).thenReturn(NOW.plusSeconds(10));
        assertThat(operations.activateDue(id)).isTrue();
        assertThat(operations.activateDue(id)).isFalse();
        check("IOS", 28).andExpect(jsonPath("$.action").value("REQUIRED"));
        assertThat(getPolicy(old.path("id").asLong()).path("status").asString()).isEqualTo("SUPERSEDED");
        assertThat(count("event_type = 'ACTIVATE' AND success = true")).isEqualTo(1);
        assertThat(getPolicy(id).path("effectiveAt").asString()).isEqualTo(NOW.plusSeconds(10).toString());
        assertThat(tree(publish(draft, "SCHEDULED", NOW.plusSeconds(10).toString(), "scheduled").andExpect(status().isOk())))
                .isEqualTo(scheduled);
    }

    @Test
    void onlyOneScheduleAndCancelIsIdempotent() throws Exception {
        JsonNode scheduled = tree(publish(create("IOS", true, 28L, true), "SCHEDULED",
                NOW.plusSeconds(60).toString(), "schedule").andExpect(status().isOk()));
        JsonNode draft = create("IOS", true, 29L, true);
        publish(draft, "SCHEDULED", NOW.plusSeconds(120).toString(), "conflict").andExpect(status().isConflict());
        publish(draft, "IMMEDIATE", null, "conflict-now").andExpect(status().isConflict());
        long id = scheduled.path("id").asLong();
        Map<String, Object> cancel = Map.of("revision", scheduled.path("revision").asLong(), "reason", REASON);
        JsonNode first = tree(command(id, "cancel", cancel, "cancel").andExpect(status().isOk()));
        assertThat(tree(command(id, "cancel", cancel, "cancel").andExpect(status().isOk()))).isEqualTo(first);
        assertThat(first.path("status").asString()).isEqualTo("CANCELLED");
        when(clock.instant()).thenReturn(NOW.plusSeconds(180));
        assertThat(operations.activateDue(id)).isFalse();
        publish(draft, "IMMEDIATE", null, "after-cancel").andExpect(status().isOk());
        command(draft.path("id").asLong(), "cancel", Map.of("revision", 2, "reason", REASON), "active-cancel")
                .andExpect(status().isConflict());
    }

    @Test
    void rollbackClonesHistoryGuardsActiveStateCancelsPendingAndReplays() throws Exception {
        JsonNode source = tree(publish(create("IOS", false, null, true), "IMMEDIATE", null, "source").andExpect(status().isOk()));
        JsonNode active = tree(publish(create("IOS", true, 28L, true), "IMMEDIATE", null, "active").andExpect(status().isOk()));
        JsonNode scheduled = tree(publish(create("IOS", true, 29L, true), "SCHEDULED",
                NOW.plusSeconds(60).toString(), "pending").andExpect(status().isOk()));
        long sourceId = source.path("id").asLong();
        Map<String, Object> body = Map.of("expectedActivePolicyId", active.path("id").asLong(),
                "expectedActiveRevision", active.path("revision").asLong(), "reason", REASON);
        Map<String, Object> stale = new HashMap<>(body);
        stale.put("expectedActiveRevision", 999);
        command(sourceId, "rollback", stale, "stale").andExpect(status().isConflict());
        JsonNode restored = tree(command(sourceId, "rollback", body, "rollback").andExpect(status().isOk()));
        assertThat(restored.path("id").asLong()).isNotEqualTo(sourceId);
        assertThat(restored.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(getPolicy(sourceId).path("status").asString()).isEqualTo("SUPERSEDED");
        assertThat(getPolicy(scheduled.path("id").asLong()).path("status").asString()).isEqualTo("CANCELLED");
        assertThat(tree(command(sourceId, "rollback", body, "rollback").andExpect(status().isOk()))).isEqualTo(restored);
        check("IOS", 1).andExpect(jsonPath("$.action").value("OPTIONAL"));
        assertThat(count("event_type = 'ROLLBACK' AND success = true")).isEqualTo(1);
        admin(get(ADMIN + "/audit-logs").param("eventType", "ROLLBACK"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].afterSnapshot.scheduledPolicy.status").value("CANCELLED"))
                .andExpect(jsonPath("$.content[0].afterSnapshot.previousActivePolicy.status").value("SUPERSEDED"));
    }

    @Test
    void relaxationHasSeparateExpectedActiveGuardAndCancelsSchedules() throws Exception {
        JsonNode active = tree(publish(create("IOS", true, 28L, true), "IMMEDIATE", null, "active").andExpect(status().isOk()));
        JsonNode relaxed = create("IOS", false, null, false);
        publish(relaxed, "IMMEDIATE", null, "normal-lowering").andExpect(status().isConflict());
        JsonNode scheduled = tree(publish(create("IOS", true, 29L, true), "SCHEDULED",
                NOW.plusSeconds(60).toString(), "schedule").andExpect(status().isOk()));
        Map<String, Object> body = Map.of("revision", 1, "expectedActivePolicyId", active.path("id").asLong(),
                "expectedActiveRevision", active.path("revision").asLong(), "storeAvailabilityConfirmed", true, "reason", REASON);
        JsonNode result = tree(command(relaxed.path("id").asLong(), "relax", body, "relax").andExpect(status().isOk()));
        assertThat(result.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(tree(command(relaxed.path("id").asLong(), "relax", body, "relax").andExpect(status().isOk()))).isEqualTo(result);
        assertThat(getPolicy(scheduled.path("id").asLong()).path("status").asString()).isEqualTo("CANCELLED");
        check("IOS", 1).andExpect(jsonPath("$.action").value("NONE"));
    }

    @Test
    void auditFailureRollsBackBothPoliciesAndCommandAndSameKeyCanRetry() throws Exception {
        JsonNode initial = tree(publish(create("IOS", true, 27L, true), "IMMEDIATE", null, "initial").andExpect(status().isOk()));
        JsonNode draft = create("IOS", true, 28L, true);
        doAnswer(invocation -> {
            AppUpdateAudit audit = invocation.getArgument(0);
            if (audit.isSuccess() && audit.getEventType().name().equals("PUBLISH")) {
                throw new DataIntegrityViolationException("test audit failure");
            }
            return invocation.callRealMethod();
        }).when(audits).saveAndFlush(any(AppUpdateAudit.class));
        publish(draft, "IMMEDIATE", null, "retry").andExpect(status().isInternalServerError());
        reset(audits);
        assertThat(getPolicy(initial.path("id").asLong()).path("status").asString()).isEqualTo("ACTIVE");
        assertThat(getPolicy(draft.path("id").asLong()).path("status").asString()).isEqualTo("DRAFT");
        assertThat(commands.existsById("retry")).isFalse();
        check("IOS", 27).andExpect(jsonPath("$.action").value("OPTIONAL"));
        publish(draft, "IMMEDIATE", null, "retry").andExpect(status().isOk());
    }

    @Test
    void auditCapturesActorRequestMetadataAndFilteredPages() throws Exception {
        JsonNode draft = create("IOS", true, 28L, true);
        publish(draft, "IMMEDIATE", null, "audit").andExpect(status().isOk());
        create("ANDROID", false, null, true);
        admin(get(ADMIN).param("platform", "IOS").param("status", "ACTIVE").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(draft.path("id").asLong()));
        admin(get(ADMIN + "/audit-logs").param("platform", "IOS").param("eventType", "PUBLISH")
                        .param("actorId", "7").param("from", NOW.toString()).param("to", NOW.plusSeconds(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actorId").value(7))
                .andExpect(jsonPath("$.content[0].publishedBy").value(7))
                .andExpect(jsonPath("$.content[0].beforeSnapshot.policy.status").value("DRAFT"))
                .andExpect(jsonPath("$.content[0].afterSnapshot.policy.status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].reason").value(REASON))
                .andExpect(jsonPath("$.content[0].requestIp").value("192.0.2.7"))
                .andExpect(jsonPath("$.content[0].userAgent").value("admin-test"))
                .andExpect(jsonPath("$.content[0].requestId").value("request-test-7"));
        admin(get(ADMIN + "/audit-logs").param("size", "1")).andExpect(jsonPath("$.hasNext").value(true));
        admin(get(ADMIN + "/audit-logs").param("from", NOW.toString()).param("to", NOW.toString()))
                .andExpect(status().isBadRequest());
        admin(get(ADMIN).param("size", "101")).andExpect(status().isBadRequest());
        admin(get(ADMIN).param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void everyAdminRouteRequiresAdminJwtIncludingPreviewAndEmergency() throws Exception {
        for (String suffix : List.of("", "/1", "/audit-logs")) {
            mvc.perform(get(ADMIN + suffix)).andExpect(status().isUnauthorized());
            mvc.perform(get(ADMIN + suffix).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isForbidden());
        }
        List<MockHttpServletRequestBuilder> mutations = new ArrayList<>();
        mutations.add(post(ADMIN));
        mutations.add(put(ADMIN + "/1"));
        for (String suffix : List.of("publish", "cancel", "rollback", "preview", "relax")) {
            mutations.add(post(ADMIN + "/1/" + suffix));
        }
        for (MockHttpServletRequestBuilder request : mutations) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(ADMIN).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminIdempotencyCorsAndPublicCorsWork() throws Exception {
        mvc.perform(options(ADMIN + "/1/publish").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key,x-request-id"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options(CHECK).header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidPreviewAndMissingPolicyUseDomainErrors() throws Exception {
        admin(get(ADMIN + "/99999")).andExpect(status().isNotFound());
        JsonNode draft = create("IOS", true, 28L, true);
        long id = draft.path("id").asLong();
        admin(post(ADMIN + "/" + id + "/preview").content(json(Map.of("revision", 999, "builds", List.of(1)))))
                .andExpect(status().isConflict());
        for (List<Long> builds : List.of(List.<Long>of(), List.of(0L), Collections.nCopies(101, 1L))) {
            admin(post(ADMIN + "/" + id + "/preview").content(json(Map.of("revision", 1, "builds", builds))))
                    .andExpect(status().isBadRequest());
        }
        admin(post(ADMIN + "/" + id + "/preview").content("{\"revision\":1,\"builds\":[1.2]}"))
                .andExpect(status().isBadRequest());
        assertThat(getPolicy(id).path("revision").asLong()).isEqualTo(1);
    }

    @Test
    void openApiDistinguishesPreviewRequestFromResponse() throws Exception {
        String spec = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/app-update-openapi.json"), spec);
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.AppUpdatePolicyPreviewRequest.properties.builds").exists())
                .andExpect(jsonPath("$.components.schemas.AppUpdatePolicyPreviewRequest.properties.results").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AppUpdatePolicyPreviewResponse.properties.results").exists())
                .andExpect(jsonPath("$.components.schemas.AppUpdatePolicyAdminResponse.properties.optionalUpdateEnabled").exists());
    }

    private Map<String, Object> content(String platform, boolean force, Long minimum, boolean optional) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("platform", platform);
        body.put("latestVersion", "1.3");
        body.put("latestBuild", 30);
        body.put("forceUpdateEnabled", force);
        body.put("minimumSupportedBuild", minimum);
        body.put("optionalUpdateEnabled", optional);
        body.put("storeUrl", platform.equals("IOS") ? "https://apps.apple.com/app/id1234567890"
                : "https://play.google.com/store/apps/details?id=com.hanseomate.app");
        body.put("title", "업데이트가 필요해요");
        body.put("message", "안정적인 서비스 이용을 위해 업데이트해 주세요.");
        body.put("reason", REASON);
        return body;
    }

    private JsonNode create(String platform, boolean force, Long minimum, boolean optional) throws Exception {
        return tree(admin(post(ADMIN).content(json(content(platform, force, minimum, optional))))
                .andExpect(status().isCreated()));
    }

    private Map<String, Object> publishBody(JsonNode policy, String mode, String effectiveAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("revision", policy.path("revision").asLong());
        body.put("publishMode", mode);
        body.put("effectiveAt", effectiveAt);
        body.put("storeAvailabilityConfirmed", true);
        body.put("reason", REASON);
        return body;
    }

    private ResultActions publish(JsonNode policy, String mode, String effectiveAt, String key) throws Exception {
        return command(policy.path("id").asLong(), "publish", publishBody(policy, mode, effectiveAt), key);
    }

    private ResultActions command(long id, String operation, Object body, String key) throws Exception {
        return admin(post(ADMIN + "/" + id + "/" + operation).header("Idempotency-Key", key).content(json(body)));
    }

    private JsonNode getPolicy(long id) throws Exception {
        return tree(admin(get(ADMIN + "/" + id)).andExpect(status().isOk()));
    }

    private ResultActions check(String platform, long build) throws Exception {
        return mvc.perform(get(CHECK).param("platform", platform).param("build", Long.toString(build)).param("version", "1.2"));
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(jwt().jwt(j -> j.subject("7").claim("role", "ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).header("User-Agent", "admin-test")
                .header("X-Request-ID", "request-test-7").with(r -> { r.setRemoteAddr("192.0.2.7"); return r; }));
    }

    private String json(Object value) { return mapper.writeValueAsString(value); }
    private JsonNode tree(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
    private long count(String where) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM app_update_audits WHERE " + where, Long.class);
    }
}
