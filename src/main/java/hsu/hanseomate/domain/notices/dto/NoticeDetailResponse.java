package hsu.hanseomate.domain.notices.dto;

import hsu.hanseomate.domain.notices.entity.Notice;
import java.time.LocalDate;
import java.util.List;

public record NoticeDetailResponse(
        Long id,
        String noticeType,
        String originNoticeId,
        String title,
        String sourceUrl,
        String contentHtml,
        String author,
        LocalDate postDate,
        boolean isHot,
        List<NoticeFileResponse> attachments
) {

    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(
                notice.getId(),
                notice.getNoticeType().value(),
                notice.getOriginNoticeId(),
                notice.getTitle(),
                notice.getSourceUrl(),
                notice.getContentHtml(),
                notice.getAuthor(),
                notice.getPostDate(),
                notice.isHot(),
                notice.getAttachments().stream()
                        .map(NoticeFileResponse::from)
                        .toList()
        );
    }
}
