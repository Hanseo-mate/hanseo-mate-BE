package hsu.hanseomate.domain.notification.controller;

import hsu.hanseomate.domain.notification.dto.NotificationResponse;
import hsu.hanseomate.domain.notification.dto.UnreadCountResponse;
import hsu.hanseomate.domain.notification.service.NotificationInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationInboxService notificationInboxService;

    /**
     * GET /api/v1/notifications?installationId=xxx&page=0&size=10
     * 최신 20개 범위 내에서 페이지 처리하여 알림 목록 반환
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @RequestParam String installationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(notificationInboxService.getNotifications(installationId, page, size));
    }

    /**
     * GET /api/v1/notifications/unread-count?installationId=xxx
     * 최신 20개 중 읽지 않은 알림 수 반환 (앱 배지 카운트용)
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestParam String installationId
    ) {
        return ResponseEntity.ok(notificationInboxService.getUnreadCount(installationId));
    }

    /**
     * PATCH /api/v1/notifications/{notificationId}/read?installationId=xxx
     * 단건 읽음 처리
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long notificationId,
            @RequestParam String installationId
    ) {
        notificationInboxService.markAsRead(notificationId, installationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/v1/notifications/read-all?installationId=xxx
     * 전체 읽음 처리 (최신 20개 중 미읽음 벌크 INSERT)
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @RequestParam String installationId
    ) {
        notificationInboxService.markAllAsRead(installationId);
        return ResponseEntity.noContent().build();
    }
}
