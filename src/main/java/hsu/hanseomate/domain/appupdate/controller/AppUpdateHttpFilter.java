package hsu.hanseomate.domain.appupdate.controller;

import hsu.hanseomate.domain.appupdate.support.AppUpdateRateLimiter;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
@RequiredArgsConstructor
public class AppUpdateHttpFilter extends OncePerRequestFilter {
    private final AppUpdateRateLimiter limiter;
    private final ObjectMapper mapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
        return !path.equals("/api/app-updates/check")
                && !path.equals("/api/admin/app-update-policies")
                && !path.startsWith("/api/admin/app-update-policies/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,128}")) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute("appUpdateRequestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        if (request.getRequestURI().equals(request.getContextPath() + "/api/app-updates/check")
                && request.getMethod().equals("GET") && !limiter.allow(request.getRemoteAddr())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                    HttpStatus.TOO_MANY_REQUESTS, "요청이 많습니다. 잠시 후 다시 시도해 주세요.", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }
}
