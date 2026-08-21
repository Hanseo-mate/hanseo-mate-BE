package hsu.hanseomate.domain.systemnotice.dto;

import hsu.hanseomate.domain.systemnotice.entity.SystemNotice;
import java.time.LocalDateTime;

public record SystemNoticeResponse(
        Long id,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SystemNoticeResponse from(SystemNotice notice) {
        return new SystemNoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
