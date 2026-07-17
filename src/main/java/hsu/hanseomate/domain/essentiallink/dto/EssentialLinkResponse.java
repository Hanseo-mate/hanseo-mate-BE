package hsu.hanseomate.domain.essentiallink.dto;

import hsu.hanseomate.domain.essentiallink.entity.EssentialLink;
import java.time.LocalDateTime;

public record EssentialLinkResponse(
        Long id,
        String name,
        String url,
        String category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EssentialLinkResponse from(EssentialLink essentialLink) {
        return new EssentialLinkResponse(
                essentialLink.getId(),
                essentialLink.getName(),
                essentialLink.getUrl(),
                essentialLink.getCategory(),
                essentialLink.getCreatedAt(),
                essentialLink.getUpdatedAt()
        );
    }
}
