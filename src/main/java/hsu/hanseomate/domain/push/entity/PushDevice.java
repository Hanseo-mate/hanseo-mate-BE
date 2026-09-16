package hsu.hanseomate.domain.push.entity;

import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 기기별 Expo Push Token 저장 엔티티.
 * installation_id와 Expo token을 함께 식별 기준으로 사용하며,
 * user_id는 비로그인 사용자를 위해 nullable입니다.
 */
@Getter
@Entity
@Table(
        name = "push_devices",
        indexes = @Index(name = "idx_push_devices_user", columnList = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 사용자의 경우 연결된 user ID (비로그인 시 null) */
    @Column(name = "user_id")
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_push_devices_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserAccount user;

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

    /**
     * 같은 설치에서 토큰 정보를 갱신합니다.
     *
     * <p>인증 없이 주기적으로 갱신하는 요청은 기존 사용자 연결을 유지합니다.
     * 사용자 연결 해제는 로그아웃 API({@link #clearUserId()})에서만 명시적으로 처리합니다.</p>
     */
    public void refreshRegistration(
            Long authenticatedUserId,
            String expoPushToken,
            String platform,
            String projectId,
            String appVersion
    ) {
        if (authenticatedUserId != null) {
            this.userId = authenticatedUserId;
        }
        this.expoPushToken = expoPushToken;
        this.platform = platform;
        this.projectId = projectId;
        this.appVersion = appVersion;
        this.isActive = true;
        this.lastRegisteredAt = LocalDateTime.now();
        this.disabledAt = null;
        this.lastErrorCode = null;
    }

    /**
     * 동일 Expo 토큰이 새로운 installationId로 등록된 경우 기존 행을 재사용합니다.
     * 새 설치 요청이 비인증 상태라면 이전 설치의 사용자 연결은 승계하지 않습니다.
     */
    public void reassignInstallation(
            Long authenticatedUserId,
            String installationId,
            String platform,
            String projectId,
            String appVersion
    ) {
        this.userId = authenticatedUserId;
        this.installationId = installationId;
        this.platform = platform;
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
