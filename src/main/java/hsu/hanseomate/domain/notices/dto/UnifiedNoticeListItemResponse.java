package hsu.hanseomate.domain.notices.dto;

import hsu.hanseomate.domain.notices.entity.Notice;
import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import java.time.LocalDate;

public record UnifiedNoticeListItemResponse(
        Long id,
        String noticeType,
        String originNoticeId,
        String title,
        String sourceUrl,
        String author,
        LocalDate postDate,
        boolean isHot,
        long viewCount // 조회수 필드 추가
) {

    // 일반 공지사항용 변환
    public static UnifiedNoticeListItemResponse from(Notice notice) {
        return new UnifiedNoticeListItemResponse(
                notice.getId(),
                notice.getNoticeType().name(), // 또는 notice.getNoticeType().value()
                notice.getOriginNoticeId(),
                notice.getTitle(),
                notice.getSourceUrl(),
                notice.getAuthor(),
                notice.getPostDate(),
                notice.isHot(),
                notice.getViewCount()
        );
    }

    // 학생회 공지사항용 변환
    public static UnifiedNoticeListItemResponse from(StudentCouncilNotice councilNotice) {
        return new UnifiedNoticeListItemResponse(
                councilNotice.getId(),
                "STUDENT_COUNCIL",      // 타입 고정
                null,                   // originNoticeId 없음
                councilNotice.getTitle(),
                null,                   // sourceUrl 없음
                null,                   // author 없음 (프론트에서 '총학생회'로 표시하거나 여기서 "총학생회"로 하드코딩 가능)
                councilNotice.getCreatedAt().toLocalDate(), // LocalDateTime을 LocalDate로 변환
                false,                  // isHot 없음
                councilNotice.getViewCount()
        );
    }
}