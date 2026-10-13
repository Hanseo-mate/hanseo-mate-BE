package hsu.hanseomate.domain.push.entity;

import hsu.hanseomate.domain.push.type.NotificationCategory;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.EnumMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 설치 단위 설정은 로그인 연결 및 Expo 토큰 변경과 독립적으로 유지합니다. */
@Getter
@Entity
@Table(name = "notification_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference extends BaseTimeEntity {
    @Id
    @Column(name = "installation_id", length = 100)
    private String installationId;

    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "notice_milestone", nullable = false)
    private boolean noticeMilestone = true;
    @Column(name = "student_council_notice", nullable = false)
    private boolean studentCouncilNotice = true;
    @Column(name = "system_notice", nullable = false)
    private boolean systemNotice = true;
    @Column(name = "club_recruitment", nullable = false)
    private boolean clubRecruitment = true;
    @Column(name = "class_reminder", nullable = false)
    private boolean classReminder = true;
    @Column(name = "shuttle_reservation", nullable = false)
    private boolean shuttleReservation = true;

    public static NotificationPreference defaults(String installationId) {
        NotificationPreference preference = new NotificationPreference();
        preference.installationId = installationId;
        return preference;
    }

    public void update(Boolean enabled, Map<NotificationCategory, Boolean> categories) {
        if (enabled != null) this.enabled = enabled;
        categories.forEach((category, value) -> {
            switch (category) {
                case NOTICE_MILESTONE -> noticeMilestone = value;
                case STUDENT_COUNCIL_NOTICE -> studentCouncilNotice = value;
                case SYSTEM_NOTICE -> systemNotice = value;
                case CLUB_RECRUITMENT -> clubRecruitment = value;
                case CLASS_REMINDER -> classReminder = value;
                case SHUTTLE_RESERVATION -> shuttleReservation = value;
            }
        });
    }

    public boolean allows(NotificationCategory category) {
        return enabled && (category == null || categories().get(category));
    }

    public Map<NotificationCategory, Boolean> categories() {
        Map<NotificationCategory, Boolean> result = new EnumMap<>(NotificationCategory.class);
        result.put(NotificationCategory.NOTICE_MILESTONE, noticeMilestone);
        result.put(NotificationCategory.STUDENT_COUNCIL_NOTICE, studentCouncilNotice);
        result.put(NotificationCategory.SYSTEM_NOTICE, systemNotice);
        result.put(NotificationCategory.CLUB_RECRUITMENT, clubRecruitment);
        result.put(NotificationCategory.CLASS_REMINDER, classReminder);
        result.put(NotificationCategory.SHUTTLE_RESERVATION, shuttleReservation);
        return result;
    }
}
