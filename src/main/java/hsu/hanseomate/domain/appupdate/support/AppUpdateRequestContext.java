package hsu.hanseomate.domain.appupdate.support;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.Authentication;

public record AppUpdateRequestContext(Long actorId, String requestIp, String userAgent, String requestId) {
    public static AppUpdateRequestContext from(Authentication authentication, HttpServletRequest request) {
        return new AppUpdateRequestContext(Long.valueOf(authentication.getName()),
                safe(request.getRemoteAddr(), 64), safe(request.getHeader("User-Agent"), 500),
                String.valueOf(request.getAttribute("appUpdateRequestId")));
    }

    public static AppUpdateRequestContext scheduler() {
        return new AppUpdateRequestContext(null, null, "app-update-scheduler", UUID.randomUUID().toString());
    }

    public static String safe(String value, int max) {
        if (value == null) return null;
        String clean = value.replaceAll("[\\p{Cc}\\p{Cf}]", "");
        return clean.codePointCount(0, clean.length()) <= max
                ? clean : clean.substring(0, clean.offsetByCodePoints(0, max));
    }
}
