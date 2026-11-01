package hsu.hanseomate.domain.push.dto;

import hsu.hanseomate.domain.push.entity.NotificationPreference;
import hsu.hanseomate.domain.push.type.NotificationCategory;
import java.util.Map;

public record NotificationPreferenceResponse(boolean enabled, Map<NotificationCategory, Boolean> categories) {
    public static NotificationPreferenceResponse from(NotificationPreference preference) {
        return new NotificationPreferenceResponse(preference.isEnabled(), preference.categories());
    }
}
