package hsu.hanseomate.domain.push.dto;

import hsu.hanseomate.domain.push.type.NotificationCategory;
import hsu.hanseomate.global.exception.BadRequestException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** PATCH에서는 생략한 값을 유지하며, 잘못된 Boolean이나 종류는 부분 저장하지 않습니다. */
public record NotificationPreferenceUpdateRequest(Boolean enabled, Map<NotificationCategory, Boolean> categories) {
    public static NotificationPreferenceUpdateRequest parse(Map<String, Object> body) {
        if (body == null || !Set.of("enabled", "categories").containsAll(body.keySet())) {
            throw invalid();
        }
        Boolean enabled = null;
        if (body.containsKey("enabled")) {
            if (!(body.get("enabled") instanceof Boolean value)) throw invalid();
            enabled = value;
        }
        Map<NotificationCategory, Boolean> categories = new EnumMap<>(NotificationCategory.class);
        if (body.containsKey("categories")) {
            if (!(body.get("categories") instanceof Map<?, ?> values)) throw invalid();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Boolean value)) {
                    throw invalid();
                }
                try {
                    categories.put(NotificationCategory.valueOf(key), value);
                } catch (IllegalArgumentException exception) {
                    throw invalid();
                }
            }
        }
        if (enabled == null && categories.isEmpty()) throw invalid();
        return new NotificationPreferenceUpdateRequest(enabled, Map.copyOf(categories));
    }

    private static BadRequestException invalid() {
        return new BadRequestException("enabled 또는 지원하는 categories에 Boolean 값을 지정해야 합니다.");
    }
}
