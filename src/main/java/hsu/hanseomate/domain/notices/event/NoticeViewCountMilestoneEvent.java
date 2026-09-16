package hsu.hanseomate.domain.notices.event;

import hsu.hanseomate.domain.notices.entity.NoticeType;

/**
 * 일반 공지 조회수가 특정 이정표(milestone)에 도달했을 때 발행되는 이벤트.
 *
 * <p>{@link hsu.hanseomate.domain.notices.service.NoticeService}가 발행하며,
 * {@link hsu.hanseomate.domain.push.listener.NotificationEventListener}가
 * {@code BEFORE_COMMIT} 페이즈에서 처리하여 동일 트랜잭션 내에 Outbox를 저장합니다.
 *
 * @param noticeId   조회수 이정표에 도달한 공지 ID
 * @param noticeType 공지 유형 (알림 제목 생성에 사용)
 * @param title      공지 제목
 * @param milestone  도달한 조회수 (예: 100)
 */
public record NoticeViewCountMilestoneEvent(
        Long noticeId,
        NoticeType noticeType,
        String title,
        long milestone
) {}
