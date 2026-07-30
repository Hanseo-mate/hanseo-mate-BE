package hsu.hanseomate.domain.notices.dto;

import hsu.hanseomate.domain.notices.entity.Notice;
import java.time.LocalDate;

public record NoticeListItemResponse(
        Long id,
        String noticeType,
        String originNoticeId,
        String title,
        String sourceUrl,
        String author,
        LocalDate postDate,
        boolean isHot
) {

    public static NoticeListItemResponse from(Notice notice) {
        return new NoticeListItemResponse(
                notice.getId(),
                notice.getNoticeType().value(),
                notice.getOriginNoticeId(),
                notice.getTitle(),
                notice.getSourceUrl(),
                notice.getAuthor(),
                notice.getPostDate(),
                notice.isHot()
        );
    }
}
