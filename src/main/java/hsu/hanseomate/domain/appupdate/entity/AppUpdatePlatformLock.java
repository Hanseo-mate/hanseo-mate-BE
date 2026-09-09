package hsu.hanseomate.domain.appupdate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_update_platform_locks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUpdatePlatformLock {
    @Id @Column(length = 16)
    private String platform;
}
