package hsu.hanseomate.domain.appupdate.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "app_update_commands")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUpdateCommand {
    @Id @Column(length = 128)
    private String idempotencyKey;
    @Column(nullable = false, length = 64)
    private String fingerprint;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(columnDefinition = "LONGTEXT")
    private String responseJson;

    public void complete(String responseJson) {
        this.responseJson = responseJson;
    }
}
