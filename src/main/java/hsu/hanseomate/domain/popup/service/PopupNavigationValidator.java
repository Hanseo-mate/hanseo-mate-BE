package hsu.hanseomate.domain.popup.service;

import hsu.hanseomate.domain.popup.dto.PopupNavigationRequest;
import hsu.hanseomate.domain.popup.model.PopupNavigation;
import hsu.hanseomate.domain.popup.type.PopupNavigationType;
import hsu.hanseomate.domain.popup.type.PopupNoticeType;
import hsu.hanseomate.global.exception.BadRequestException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PopupNavigationValidator {

    private static final short SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_URL_LENGTH = 2048;
    private static final Set<PopupNavigationType> STATIC_TYPES = EnumSet.of(
            PopupNavigationType.HOME,
            PopupNavigationType.NOTICE_LIST,
            PopupNavigationType.CLUB_LIST,
            PopupNavigationType.CAFETERIA,
            PopupNavigationType.CALENDAR,
            PopupNavigationType.TIMETABLE,
            PopupNavigationType.CAMPUS_MAP,
            PopupNavigationType.SYSTEM_NOTICE_LIST,
            PopupNavigationType.FESTIVAL
    );

    public PopupNavigation validateRequired(
            boolean navigationProvided,
            PopupNavigationRequest request
    ) {
        if (!navigationProvided) {
            throw invalid("navigation", "필수 필드입니다. 이동이 없으면 null을 전달해야 합니다.");
        }
        if (request == null) {
            return null;
        }

        short schemaVersion = validateSchemaVersion(request.schemaVersion());
        PopupNavigationType type = validateType(request.type());
        Map<String, Object> params = validateParams(type, request.params());
        return new PopupNavigation(schemaVersion, type, params);
    }

    public String validateOptionalLinkUrl(String linkUrl) {
        if (linkUrl == null || linkUrl.isBlank()) {
            return null;
        }
        return validateHttpsUrl(linkUrl, "linkUrl");
    }

    private short validateSchemaVersion(Integer schemaVersion) {
        if (schemaVersion == null) {
            throw invalid("navigation.schemaVersion", "필수입니다.");
        }
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw invalid("navigation.schemaVersion", "지원하지 않는 버전입니다.");
        }
        return schemaVersion.shortValue();
    }

    private PopupNavigationType validateType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw invalid("navigation.type", "필수입니다.");
        }
        try {
            return PopupNavigationType.valueOf(rawType);
        } catch (IllegalArgumentException exception) {
            throw invalid("navigation.type", "지원하지 않는 이동 유형입니다.");
        }
    }

    private Map<String, Object> validateParams(
            PopupNavigationType type,
            Map<String, Object> params
    ) {
        if (STATIC_TYPES.contains(type)) {
            if (params != null) {
                throw invalid("navigation.params", type + " 이동에는 params를 전달할 수 없습니다.");
            }
            return null;
        }

        return switch (type) {
            case NOTICE_DETAIL -> validateNoticeDetail(params);
            case CLUB_DETAIL -> validateClubDetail(params);
            case EXTERNAL_URL -> validateExternalUrl(params);
            default -> throw new IllegalStateException("검증되지 않은 팝업 이동 유형입니다. type=" + type);
        };
    }

    private Map<String, Object> validateNoticeDetail(Map<String, Object> params) {
        requireExactKeys(params, Set.of("noticeId", "noticeType"), "NOTICE_DETAIL");
        long noticeId = positiveLong(params.get("noticeId"), "navigation.params.noticeId");
        PopupNoticeType noticeType = noticeType(params.get("noticeType"));

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("noticeId", noticeId);
        normalized.put("noticeType", noticeType.name());
        return normalized;
    }

    private Map<String, Object> validateClubDetail(Map<String, Object> params) {
        requireExactKeys(params, Set.of("clubId"), "CLUB_DETAIL");
        long clubId = positiveLong(params.get("clubId"), "navigation.params.clubId");
        return Map.of("clubId", clubId);
    }

    private Map<String, Object> validateExternalUrl(Map<String, Object> params) {
        requireExactKeys(params, Set.of("url"), "EXTERNAL_URL");
        Object rawUrl = params.get("url");
        if (!(rawUrl instanceof String url)) {
            throw invalid("navigation.params.url", "HTTPS 절대 URL 문자열이어야 합니다.");
        }
        return Map.of("url", validateHttpsUrl(url, "navigation.params.url"));
    }

    private String validateHttpsUrl(String url, String path) {
        String normalizedUrl = url.trim();
        if (normalizedUrl.length() > MAX_URL_LENGTH) {
            throw invalid(path, "2048자 이하여야 합니다.");
        }

        try {
            URI uri = new URI(normalizedUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw invalid(
                        path,
                        "사용자 정보가 없는 HTTPS 절대 URL이어야 합니다."
                );
            }
        } catch (URISyntaxException exception) {
            throw invalid(path, "올바른 HTTPS 절대 URL이어야 합니다.");
        }
        return normalizedUrl;
    }

    private void requireExactKeys(
            Map<String, Object> params,
            Set<String> expectedKeys,
            String type
    ) {
        if (params == null) {
            throw invalid("navigation.params", type + " 이동에 필요한 params가 없습니다.");
        }
        if (!params.keySet().equals(expectedKeys)) {
            throw invalid(
                    "navigation.params",
                    type + " 이동에는 " + expectedKeys + " 필드만 전달해야 합니다."
            );
        }
    }

    private long positiveLong(Object value, String path) {
        if (!(value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigInteger)) {
            throw invalid(path, "1 이상의 정수여야 합니다.");
        }

        long number;
        if (value instanceof BigInteger bigInteger) {
            try {
                number = bigInteger.longValueExact();
            } catch (ArithmeticException exception) {
                throw invalid(path, "1 이상의 정수여야 합니다.");
            }
        } else {
            number = ((Number) value).longValue();
        }
        if (number < 1) {
            throw invalid(path, "1 이상의 정수여야 합니다.");
        }
        return number;
    }

    private PopupNoticeType noticeType(Object value) {
        if (!(value instanceof String rawNoticeType) || rawNoticeType.isBlank()) {
            throw invalid("navigation.params.noticeType", "필수 enum 값입니다.");
        }
        try {
            return PopupNoticeType.valueOf(rawNoticeType);
        } catch (IllegalArgumentException exception) {
            throw invalid("navigation.params.noticeType", "지원하지 않는 공지 유형입니다.");
        }
    }

    private BadRequestException invalid(String path, String message) {
        return new BadRequestException(path + ": " + message);
    }
}
