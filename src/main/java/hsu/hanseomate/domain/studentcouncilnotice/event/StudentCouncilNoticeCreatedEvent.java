package hsu.hanseomate.domain.studentcouncilnotice.event;

/**
 * 학생회 공지가 새로 작성(Create)되었을 때 발행되는 이벤트.
 *
 * <p>{@link hsu.hanseomate.domain.studentcouncilnotice.service.StudentCouncilNoticeService}가 발행하며,
 * {@link hsu.hanseomate.domain.push.listener.NotificationEventListener}가
 * {@code BEFORE_COMMIT} 페이즈에서 처리합니다.
 *
 * <p>조회수 100회 알림과 달리, 학생회 공지는 <strong>작성 시에만</strong> 알림이 발송됩니다.
 * 이후 조회수가 100회를 넘어도 추가 알림이 발송되지 않습니다.
 *
 * @param noticeId 생성된 학생회 공지 ID
 * @param title    공지 제목
 */
public record StudentCouncilNoticeCreatedEvent(
        Long noticeId,
        String title
) {}
