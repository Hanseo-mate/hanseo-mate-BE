package hsu.hanseomate.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "payload_data", columnDefinition = "TEXT")
    private String payloadData;

    /** null이면 전체 알림, 값이 있으면 해당 로그인 사용자에게만 노출됩니다. */
    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Notification(String title, String body, String payloadData, Long targetUserId) {
        this.title = title;
        this.body = body;
        this.payloadData = payloadData;
        this.targetUserId = targetUserId;
    }
}
