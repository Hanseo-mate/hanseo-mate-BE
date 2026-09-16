package hsu.hanseomate.domain.notices.dto;

import hsu.hanseomate.domain.notices.entity.Notice;
import java.util.List;
import org.springframework.data.domain.Page;

public record NoticePageResponse(
        List<NoticeListItemResponse> items,
        int page,
        int size,
        int totalPages,
        long totalElements,
        boolean hasNext
) {

    public static NoticePageResponse from(Page<Notice> noticePage) {
        return new NoticePageResponse(
                noticePage.getContent().stream()
                        .map(NoticeListItemResponse::from)
                        .toList(),
                noticePage.getNumber(),
                noticePage.getSize(),
                noticePage.getTotalPages(),
                noticePage.getTotalElements(),
                noticePage.hasNext()
        );
    }
}
