package hsu.hanseomate.domain.appupdate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import hsu.hanseomate.domain.appupdate.config.AppUpdateProperties;
import hsu.hanseomate.domain.appupdate.controller.AppUpdateHttpFilter;
import hsu.hanseomate.domain.appupdate.repository.AppUpdatePolicyRepository;
import hsu.hanseomate.domain.appupdate.service.AppUpdatePolicyOperations;
import hsu.hanseomate.domain.appupdate.support.AppUpdateRateLimiter;
import hsu.hanseomate.domain.appupdate.worker.AppUpdateScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class AppUpdateInfrastructureTest {
    @Test
    void rateLimitIsPerIpBoundedAndResetsNextMinute() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-09T04:00:00Z"));
        AppUpdateProperties properties = new AppUpdateProperties();
        properties.setCheckRequestsPerMinute(2);
        properties.setRateLimitMaxIps(2);
        AppUpdateRateLimiter limiter = new AppUpdateRateLimiter(properties, clock);
        assertThat(limiter.allow("192.0.2.1")).isTrue();
        assertThat(limiter.allow("192.0.2.1")).isTrue();
        assertThat(limiter.allow("192.0.2.1")).isFalse();
        assertThat(limiter.allow("192.0.2.2")).isTrue();
        assertThat(limiter.allow("192.0.2.3")).isFalse();
        when(clock.instant()).thenReturn(Instant.parse("2026-09-09T04:01:00Z"));
        assertThat(limiter.allow("192.0.2.1")).isTrue();
        assertThat(limiter.allow("192.0.2.3")).isTrue();
    }

    @Test
    void rateLimitedResponseIs429AndForgedForwardedHeaderDoesNotBypassIt() throws Exception {
        AppUpdateProperties properties = new AppUpdateProperties();
        properties.setCheckRequestsPerMinute(1);
        AppUpdateRateLimiter limiter = new AppUpdateRateLimiter(properties,
                Clock.fixed(Instant.parse("2026-09-09T04:00:00Z"), java.time.ZoneOffset.UTC));
        AppUpdateHttpFilter filter = new AppUpdateHttpFilter(limiter, JsonMapper.builder().build());
        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/app-updates/check");
        first.setRemoteAddr("192.0.2.1");
        filter.doFilter(first, new MockHttpServletResponse(), (request, response) -> {});
        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/app-updates/check");
        second.setRemoteAddr("192.0.2.1");
        second.addHeader("X-Forwarded-For", "198.51.100.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(second, response, (request, result) -> { throw new AssertionError("Must be limited"); });
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(JsonMapper.builder().build().readTree(response.getContentAsString()).path("status").asInt()).isEqualTo(429);
    }

    @Test
    void schedulerFailureDoesNotStopOtherPlatformAndNextCycleRetries() {
        AppUpdatePolicyRepository policies = mock(AppUpdatePolicyRepository.class);
        AppUpdatePolicyOperations operations = mock(AppUpdatePolicyOperations.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-09T04:00:00Z"), java.time.ZoneOffset.UTC);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        when(policies.findDueIds(clock.instant())).thenReturn(List.of(1L, 2L), List.of(1L));
        when(operations.activateDue(1L)).thenThrow(new IllegalStateException("temporary")).thenReturn(true);
        when(operations.activateDue(2L)).thenReturn(true);
        AppUpdateScheduler scheduler = new AppUpdateScheduler(policies, operations, clock, metrics);
        scheduler.activateDuePolicies();
        verify(operations).activateDue(2L);
        scheduler.activateDuePolicies();
        verify(operations, times(2)).activateDue(1L);
        assertThat(metrics.counter("app.update.scheduler.failures", "stage", "activate").count()).isEqualTo(1);
        assertThat(metrics.counter("app.update.scheduler.activations").count()).isEqualTo(2);
    }

    @Test
    void schedulerQueryFailureEmitsFailureMetricAndRecovers() {
        AppUpdatePolicyRepository policies = mock(AppUpdatePolicyRepository.class);
        AppUpdatePolicyOperations operations = mock(AppUpdatePolicyOperations.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-09T04:00:00Z"), java.time.ZoneOffset.UTC);
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        when(policies.findDueIds(clock.instant())).thenThrow(new IllegalStateException("database unavailable")).thenReturn(List.of());
        AppUpdateScheduler scheduler = new AppUpdateScheduler(policies, operations, clock, metrics);
        scheduler.activateDuePolicies();
        scheduler.activateDuePolicies();
        assertThat(metrics.counter("app.update.scheduler.failures", "stage", "query").count()).isEqualTo(1);
        verifyNoInteractions(operations);
    }
}
