package hsu.hanseomate.domain.studentcouncilnotice.dto;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNoticeAttachment;
import java.time.LocalDateTime;

public record StudentCouncilNoticeAttachmentResponse(
        Long id,
        String fileName,
        String downloadUrl,
        String contentType,
        long fileSize,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static StudentCouncilNoticeAttachmentResponse from(
            StudentCouncilNoticeAttachment attachment,
            String downloadUrl
    ) {
        return new StudentCouncilNoticeAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFileName(),
                downloadUrl,
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getCreatedAt(),
                attachment.getUpdatedAt()
        );
    }
}
