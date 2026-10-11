package hsu.hanseomate.domain.push.type;

import java.util.Map;

public enum NotificationCategory {
    NOTICE_MILESTONE,
    STUDENT_COUNCIL_NOTICE,
    SYSTEM_NOTICE,
    CLUB_RECRUITMENT,
    CLASS_REMINDER,
    SHUTTLE_RESERVATION;

    /** 프론트가 알림 종류를 구분할 수 있도록 payload의 category를 정합니다. */
    public static NotificationCategory resolve(Map<String, Object> data) {
        if (data == null) return null;
        Object category = data.get("category");
        if (category != null) return valueOf(category.toString());
        return switch (String.valueOf(data.get("type"))) {
            case "notice" -> NOTICE_MILESTONE;
            case "system_notice" -> SYSTEM_NOTICE;
            case "club_recruitment" -> CLUB_RECRUITMENT;
            case "schedule" -> CLASS_REMINDER;
            case "shuttle_reservation" -> SHUTTLE_RESERVATION;
            default -> null;
        };
    }
}
