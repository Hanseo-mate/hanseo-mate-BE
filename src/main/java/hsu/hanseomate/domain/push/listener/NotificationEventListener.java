package hsu.hanseomate.domain.push.listener;

import hsu.hanseomate.domain.notices.event.NoticeViewCountMilestoneEvent;
import hsu.hanseomate.domain.push.service.NotificationService;
import hsu.hanseomate.domain.studentcouncilnotice.event.StudentCouncilNoticeCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 비즈니스 이벤트를 수신하여 {@code notification_outbox} 테이블에 알림 발송 요청을 기록합니다.
 *
 * <h3>설계 의도</h3>
 * <ul>
 *   <li>{@code @TransactionalEventListener(phase = BEFORE_COMMIT)}: 이벤트를 발행한
 *       트랜잭션이 커밋되기 직전에 실행되므로, Outbox INSERT가 메인 비즈니스 변경과
 *       <strong>동일 트랜잭션 내에서 원자적으로</strong> 처리됩니다.</li>
 *   <li>메인 트랜잭션이 롤백되면 Outbox INSERT도 함께 롤백되어 데이터 정합성을 보장합니다.</li>
 *   <li>실제 Expo Push API 호출은 이 리스너가 담당하지 않습니다.
 *       {@link hsu.hanseomate.domain.push.worker.NotificationSendWorker}가
 *       주기적으로 Outbox를 읽어 발송합니다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    /**
     * 일반 공지 조회수 이정표 달성 이벤트 처리.
     *
     * <p>알림 포맷:
     * <ul>
     *   <li>title: "[{noticeType 한국어}] {공지 제목}"</li>
     *   <li>body: "해당 공지가 조회수 {N}회를 돌파하며 화제가 되고 있어요!"</li>
     *   <li>data.type: "notice", data.route: "/notices", data.entityId: noticeId</li>
     * </ul>
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onNoticeViewCountMilestone(NoticeViewCountMilestoneEvent event) {
        String title = "[%s] %s".formatted(
                event.noticeType().displayName(),
                event.title()
        );
        String body = "해당 공지가 조회수 %d회를 돌파하며 화제가 되고 있어요!".formatted(event.milestone());

        log.info("[NotificationEvent] Notice milestone: noticeId={}, viewCount={}",
                event.noticeId(), event.milestone());

        notificationService.enqueueNoticeNotification(title, body, event.noticeId().toString());
    }

    /**
     * 학생회 공지 신규 작성 이벤트 처리.
     *
     * <p>알림 포맷:
     * <ul>
     *   <li>title: "[학생회 공지] {공지 제목}"</li>
     *   <li>body: "총학생회에서 새로운 공지를 등록했습니다."</li>
     *   <li>data.type: "notice", data.route: "/notices", data.entityId: noticeId</li>
     * </ul>
     *
     * <p>학생회 공지는 <strong>작성 시에만</strong> 이 이벤트가 발행됩니다.
     * 조회수 증가 로직({@code getNoticeAndIncrementViewCount})에서는 이 이벤트를
     * 발행하지 않으므로, 100회 조회 달성 시 추가 알림이 발송되지 않습니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStudentCouncilNoticeCreated(StudentCouncilNoticeCreatedEvent event) {
        String title = "[학생회 공지] %s".formatted(event.title());
        String body = "총학생회에서 새로운 공지를 등록했습니다.";

        log.info("[NotificationEvent] StudentCouncilNotice created: noticeId={}", event.noticeId());

        notificationService.enqueueNoticeNotification(title, body, event.noticeId().toString());
    }
}
