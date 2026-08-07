package hsu.hanseomate.domain.studentcouncilnotice.dto;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import java.time.LocalDateTime;

public record StudentCouncilNoticeListItemResponse(
        Long id,
        String title,
        String author,
        String content,
        long viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static StudentCouncilNoticeListItemResponse from(StudentCouncilNotice notice) {
        return new StudentCouncilNoticeListItemResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getAuthor(),
                notice.getContent(),
                notice.getViewCount(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
