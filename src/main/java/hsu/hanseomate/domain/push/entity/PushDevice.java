package hsu.hanseomate.domain.push.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기기별 Expo Push Token 저장 엔티티.
 * installation_id 기준으로 upsert하며, user_id는 비로그인 사용자를 위해 nullable입니다.
 */
@Getter
@Entity
@Table(name = "push_devices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 사용자의 경우 연결된 user ID (비로그인 시 null) */
    @Column(name = "user_id")
    private Long userId;

    /** 앱 설치 단위 UUID — 식별 기준 */
    @Column(name = "installation_id", nullable = false, unique = true, length = 100)
    private String installationId;

    /** Expo Push Service가 발급한 기기 토큰 */
    @Column(name = "expo_push_token", nullable = false, unique = true, length = 200)
    private String expoPushToken;

    /** ios | android */
    @Column(name = "platform", nullable = false, length = 10)
    private String platform;

    @Column(name = "project_id", nullable = false, length = 100)
    private String projectId;

    @Column(name = "app_version", nullable = false, length = 20)
    private String appVersion;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "last_registered_at", nullable = false)
    private LocalDateTime lastRegisteredAt;

    /** 토큰 비활성화 시각 (nullable) */
    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    /** 마지막 에러 코드 e.g. DeviceNotRegistered (nullable) */
    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    public static PushDevice create(
            Long userId,
            String installationId,
            String expoPushToken,
            String platform,
            String projectId,
            String appVersion
    ) {
        PushDevice device = new PushDevice();
        device.userId = userId;
        device.installationId = installationId;
        device.expoPushToken = expoPushToken;
        device.platform = platform;
        device.projectId = projectId;
        device.appVersion = appVersion;
        device.isActive = true;
        device.lastRegisteredAt = LocalDateTime.now();
        return device;
    }

    /** 토큰 재등록(upsert) 시 기존 레코드 갱신 */
    public void update(Long userId, String expoPushToken, String projectId, String appVersion) {
        this.userId = userId;
        this.expoPushToken = expoPushToken;
        this.projectId = projectId;
        this.appVersion = appVersion;
        this.isActive = true;
        this.lastRegisteredAt = LocalDateTime.now();
        this.disabledAt = null;
        this.lastErrorCode = null;
    }

    /** DeviceNotRegistered 등 에러 발생 시 비활성화 */
    public void deactivate(String errorCode) {
        this.isActive = false;
        this.disabledAt = LocalDateTime.now();
        this.lastErrorCode = errorCode;
    }

    /** 로그아웃 시 user_id 연결 해제 */
    public void clearUserId() {
        this.userId = null;
    }
}
