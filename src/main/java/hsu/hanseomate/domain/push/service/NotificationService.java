package hsu.hanseomate.domain.push.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.notification.entity.Notification;
import hsu.hanseomate.domain.notification.repository.NotificationRepository;
import hsu.hanseomate.domain.push.dto.NotificationPayload;
import hsu.hanseomate.domain.push.entity.NotificationOutbox;
import hsu.hanseomate.domain.push.repository.NotificationOutboxRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import hsu.hanseomate.domain.timetable.reminder.support.TimetableReminderPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발송 요청을 Outbox 테이블에 기록하는 서비스.
 *
 * <p>공지 저장 / 크롤링 완료 등의 이벤트에서 이 서비스를 호출하면
 * Worker가 비동기로 처리합니다. 가이드 6번의 알림 데이터 규격을 준수합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    /** 공지사항 알림 Enqueue */
    @Transactional
    public void enqueueNoticeNotification(String title, String body, String entityId) {
        Map<String, Object> data = Map.of(
                "version", 1,
                "type", "notice",
                "route", "/notices",
                "entityId", entityId
        );
        enqueue(title, body, data);
    }

    /** 시스템 공지 알림 Enqueue */
    @Transactional
    public void enqueueSystemNoticeNotification(String title, String body, String entityId) {
        Map<String, Object> data = Map.of(
                "version", 1,
                "type", "system_notice",
                "route", "/system-notices",
                "entityId", entityId
        );
        enqueue(title, body, data);
    }

    /** 시간표 알림 Enqueue */
    @Transactional
    public void enqueueScheduleNotification(String title, String body) {
        Map<String, Object> data = Map.of(
                "version", 1,
                "type", "schedule",
                "route", "/timetable"
        );
        enqueue(title, body, data);
    }

    /** 테스트 알림 Enqueue (관리자 전용) */
    @Transactional
    public void enqueueTestNotification(String title, String body) {
        Map<String, Object> data = Map.of(
                "version", 1,
                "type", "test",
                "route", "/notifications"
        );
        enqueue(title, body, data);
    }

    /** 동아리 모집공고 작성·수정 알림을 공고 변경 당시의 찜 사용자에게만 Enqueue합니다. */
    @Transactional
    public void enqueueClubRecruitmentNotification(
            String title,
            String body,
            String clubId,
            Collection<Long> recipientUserIds
    ) {
        Map<String, Object> data = Map.of(
                "version", 1,
                "type", "club_recruitment",
                "route", "/clubs",
                "entityId", clubId
        );
        new LinkedHashSet<>(recipientUserIds).stream()
                .filter(java.util.Objects::nonNull)
                .forEach(userId -> enqueue(title, body, data, userId));
    }

    /** 개인 시간표의 실제 수업 시작 1시간 전 알림입니다. 전체 발송은 허용하지 않습니다. */
    @Transactional
    public void enqueueTimetableClassReminder(
            Long userId, Long timetableId, Long timetableCourseId,
            int year, int semester, String courseName, OffsetDateTime startsAt
    ) {
        Objects.requireNonNull(userId, "Class reminder requires a target user");
        String title = "[수업 알림] " + courseName;
        if (title.length() > 255) {
            title = title.substring(0, 255);
        }
        Map<String, Object> data = Map.of(
                "version", 1,
                "type", "schedule",
                "subType", "class_start_reminder",
                "route", "/timetable",
                "entityId", timetableId.toString(),
                "timetableCourseId", timetableCourseId.toString(),
                "year", year,
                "semester", semester,
                "startsAt", startsAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "minutesBefore", TimetableReminderPolicy.MINUTES_BEFORE
        );
        LocalDateTime expiresAt = startsAt
                .minusMinutes(TimetableReminderPolicy.MINUTES_BEFORE)
                .plusMinutes(TimetableReminderPolicy.EXPIRY_MINUTES)
                .withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        enqueue(title, courseName + " 수업 시작 1시간 전입니다.", data, userId, expiresAt);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void enqueue(String title, String body, Map<String, Object> data) {
        enqueue(title, body, data, null);
    }

    private void enqueue(
            String title,
            String body,
            Map<String, Object> data,
            Long targetUserId
    ) {
        enqueue(title, body, data, targetUserId, null);
    }

    private void enqueue(
            String title,
            String body,
            Map<String, Object> data,
            Long targetUserId,
            LocalDateTime expiresAt
    ) {
        try {
            String dataJson = objectMapper.writeValueAsString(data);
            String payloadJson = objectMapper.writeValueAsString(new NotificationPayload(title, body, data));

            // 인앱 알림함에 저장
            notificationRepository.save(Notification.builder()
                    .title(title)
                    .body(body)
                    .payloadData(dataJson)
                    .targetUserId(targetUserId)
                    .build());

            // Expo 발송 Outbox에 저장
            outboxRepository.save(NotificationOutbox.create(payloadJson, targetUserId, expiresAt));

            log.info("Enqueued notification title=\"{}\", targetUserId={}", title, targetUserId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification payload", e);
            throw new IllegalStateException("Failed to serialize notification payload", e);
        }
    }
}
