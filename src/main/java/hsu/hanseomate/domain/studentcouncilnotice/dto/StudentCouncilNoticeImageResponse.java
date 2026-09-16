package hsu.hanseomate.domain.studentcouncilnotice.dto;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNoticeImage;
import java.time.LocalDateTime;

public record StudentCouncilNoticeImageResponse(
        Long id,
        String fileName,
        String imageUrl,
        String contentType,
        long fileSize,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static StudentCouncilNoticeImageResponse from(StudentCouncilNoticeImage image) {
        return from(image, image.getImageUrl());
    }

    public static StudentCouncilNoticeImageResponse from(
            StudentCouncilNoticeImage image,
            String imageUrl
    ) {
        return new StudentCouncilNoticeImageResponse(
                image.getId(),
                image.getOriginalFileName(),
                imageUrl,
                image.getContentType(),
                image.getFileSize(),
                image.getCreatedAt(),
                image.getUpdatedAt()
        );
    }
}
