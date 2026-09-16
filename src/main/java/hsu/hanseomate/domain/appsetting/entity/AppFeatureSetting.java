package hsu.hanseomate.domain.appsetting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Entity
@Table(name = "app_feature_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppFeatureSetting {

    @Id
    @Column(name = "setting_key", length = 64, nullable = false)
    private String key;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    public void changeEnabled(boolean enabled, Long adminId, Instant changedAt) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        this.updatedBy = adminId;
        this.updatedAt = changedAt;
    }
}
