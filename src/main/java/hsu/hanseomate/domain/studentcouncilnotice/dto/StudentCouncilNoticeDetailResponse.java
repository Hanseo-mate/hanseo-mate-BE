package hsu.hanseomate.domain.studentcouncilnotice.dto;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import java.time.LocalDateTime;
import java.util.List;

public record StudentCouncilNoticeDetailResponse(
        Long id,
        String title,
        String author,
        String content,
        long viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<StudentCouncilNoticeImageResponse> images,
        List<StudentCouncilNoticeAttachmentResponse> attachments
) {

    public static StudentCouncilNoticeDetailResponse from(StudentCouncilNotice notice) {
        return from(notice, List.of(), List.of());
    }

    public static StudentCouncilNoticeDetailResponse from(
            StudentCouncilNotice notice,
            List<StudentCouncilNoticeImageResponse> images,
            List<StudentCouncilNoticeAttachmentResponse> attachments
    ) {
        return new StudentCouncilNoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getAuthor(),
                notice.getContent(),
                notice.getViewCount(),
                notice.getCreatedAt(),
                notice.getUpdatedAt(),
                images == null ? List.of() : List.copyOf(images),
                attachments == null ? List.of() : List.copyOf(attachments)
        );
    }
}
