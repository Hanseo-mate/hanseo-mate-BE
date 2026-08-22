package hsu.hanseomate.domain.systemnotice.event;

/**
 * 시스템 공지가 신규 등록된 직후 발행되는 이벤트입니다.
 *
 * @param noticeId 생성된 시스템 공지 ID
 * @param title 시스템 공지 제목
 */
public record SystemNoticeCreatedEvent(
        Long noticeId,
        String title
) {}
