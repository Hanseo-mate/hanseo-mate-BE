package hsu.hanseomate.domain.appupdate.support;

import static hsu.hanseomate.domain.appupdate.exception.AppUpdateException.badRequest;

import hsu.hanseomate.domain.appupdate.config.AppUpdateProperties;
import hsu.hanseomate.domain.appupdate.dto.AppUpdateRequests.ContentInput;
import hsu.hanseomate.domain.appupdate.dto.PolicyContent;
import hsu.hanseomate.domain.appupdate.type.AppPlatform;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppUpdateValidator {
    private final AppUpdateProperties properties;

    public PolicyContent content(AppPlatform platform, ContentInput input) {
        if (platform == null) throw badRequest("platform: IOS 또는 ANDROID가 필요합니다.");
        long latest = positive(input.latestBuild(), "latestBuild");
        if (input.forceUpdateEnabled() == null || input.optionalUpdateEnabled() == null) {
            throw badRequest("forceUpdateEnabled, optionalUpdateEnabled: JSON boolean이 필요합니다.");
        }
        if (input.forceUpdateEnabled()) {
            long minimum = positive(input.minimumSupportedBuild(), "minimumSupportedBuild");
            if (minimum > latest) throw badRequest("minimumSupportedBuild: latestBuild 이하여야 합니다.");
        } else if (input.minimumSupportedBuild() != null) {
            throw badRequest("minimumSupportedBuild: 강제 업데이트를 끄면 null이어야 합니다.");
        }
        return new PolicyContent(
                plainText(input.latestVersion(), 1, 32, "latestVersion"), latest,
                input.forceUpdateEnabled(), input.minimumSupportedBuild(), input.optionalUpdateEnabled(),
                storeUrl(platform, input.storeUrl()),
                plainText(input.title(), 2, 40, "title"),
                plainText(input.message(), 1, 300, "message"));
    }

    public String storeUrl(AppPlatform platform, String value) {
        String url = plainText(value, 1, 500, "storeUrl");
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw badRequest("storeUrl: 사용자 정보와 fragment가 없는 HTTPS 스토어 URL이 필요합니다.");
            }
            if (platform == AppPlatform.IOS) {
                if (properties.getIosAppStoreId().isBlank()) {
                    throw badRequest("storeUrl: 서버의 APP_UPDATES_IOS_APP_STORE_ID 설정이 필요합니다.");
                }
                String id = java.util.regex.Pattern.quote(properties.getIosAppStoreId());
                if (!"apps.apple.com".equalsIgnoreCase(uri.getHost())
                        || !uri.getRawPath().matches("/(?:[a-zA-Z]{2}/)?app/(?:[^/]+/)?id" + id + "/?")) {
                    throw badRequest("storeUrl: 설정된 한서메이트 App Store ID와 일치해야 합니다.");
                }
            } else {
                if (!"play.google.com".equalsIgnoreCase(uri.getHost())
                        || !"/store/apps/details".equals(uri.getRawPath())) {
                    throw badRequest("storeUrl: Google Play 앱 상세 URL이 필요합니다.");
                }
                List<String[]> pairs = uri.getRawQuery() == null ? List.of()
                        : Arrays.stream(uri.getRawQuery().split("&", -1))
                                .map(part -> part.split("=", 2)).toList();
                List<String[]> ids = pairs.stream().filter(pair -> decode(pair[0]).equals("id")).toList();
                if (ids.size() != 1 || ids.get(0).length != 2
                        || !properties.getAndroidPackageId().equals(decode(ids.get(0)[1]))) {
                    throw badRequest("storeUrl: 한서메이트 Android 패키지 ID와 일치해야 합니다.");
                }
            }
            return url;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw badRequest("storeUrl: 유효한 HTTPS 스토어 URL이 필요합니다.");
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    public static long positive(Long value, String field) {
        if (value == null || value < 1) throw badRequest(field + ": 1 이상의 정수가 필요합니다.");
        return value;
    }

    public static String plainText(String value, int min, int max, String field) {
        if (value == null) throw badRequest(field + ": 필수 값입니다.");
        // 개행과 제어 문자도 저장 전에 거부해 로그와 화면에 원문을 안전하게 전달한다.
        if (value.codePoints().anyMatch(c -> Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT
                || Character.getType(c) == Character.LINE_SEPARATOR
                || Character.getType(c) == Character.PARAGRAPH_SEPARATOR) || value.contains("<") || value.contains(">")) {
            throw badRequest(field + ": HTML, 스크립트 및 제어 문자를 사용할 수 없습니다.");
        }
        String trimmed = value.replaceAll("^[\\p{Z}\\s]+|[\\p{Z}\\s]+$", "");
        int length = trimmed.codePointCount(0, trimmed.length());
        if (trimmed.isBlank() || length < min || length > max) {
            throw badRequest(field + ": 공백 제거 후 " + min + "~" + max + "자여야 합니다.");
        }
        return trimmed;
    }

    public static String reason(String reason) {
        return plainText(reason, 10, 500, "reason");
    }

    public static void revision(Long expected, long actual) {
        positive(expected, "revision");
        if (expected != actual) {
            throw hsu.hanseomate.domain.appupdate.exception.AppUpdateException.conflict(
                    "revision이 변경되었습니다. 최신 정책을 조회해 주세요.");
        }
    }

    public static Instant utc(String value, String field) {
        if (value == null) return null;
        try {
            if (!value.endsWith("Z")) throw new DateTimeParseException("UTC required", value, 0);
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw badRequest(field + ": ISO 8601 UTC(Z) 시각이 필요합니다.");
        }
    }
}
