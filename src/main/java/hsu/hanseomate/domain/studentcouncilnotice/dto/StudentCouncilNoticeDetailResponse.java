package hsu.hanseomate.domain.studentcouncilnotice.dto;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import java.time.LocalDateTime;

public record StudentCouncilNoticeDetailResponse(
        Long id,
        String title,
        String author,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static StudentCouncilNoticeDetailResponse from(StudentCouncilNotice notice) {
        return new StudentCouncilNoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getAuthor(),
                notice.getContent(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}
