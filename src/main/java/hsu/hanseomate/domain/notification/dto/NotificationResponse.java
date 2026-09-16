package hsu.hanseomate.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String body,
        String payloadData,
        boolean isRead,
        @Schema(description = "한국 시간대(+09:00)를 포함한 알림 생성 시각",
                example = "2026-09-16T15:00:00+09:00")
        OffsetDateTime createdAt
) {}
