package hsu.hanseomate.domain.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String body,
        String payloadData,
        boolean isRead,
        LocalDateTime createdAt
) {}
