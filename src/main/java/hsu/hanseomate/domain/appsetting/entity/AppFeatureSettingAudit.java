package hsu.hanseomate.domain.appsetting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(
        name = "app_feature_setting_audits",
        indexes = @Index(name = "idx_feature_setting_audits_key_id", columnList = "setting_key,id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppFeatureSettingAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 64, updatable = false)
    private String settingKey;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "previous_enabled", nullable = false, updatable = false)
    private boolean previousEnabled;

    @Column(name = "new_enabled", nullable = false, updatable = false)
    private boolean newEnabled;

    @Column(name = "request_ip", nullable = false, length = 64, updatable = false)
    private String requestIp;

    public AppFeatureSettingAudit(
            String settingKey,
            Long changedBy,
            Instant changedAt,
            boolean previousEnabled,
            boolean newEnabled,
            String requestIp
    ) {
        this.settingKey = settingKey;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.previousEnabled = previousEnabled;
        this.newEnabled = newEnabled;
        this.requestIp = requestIp;
    }
}
