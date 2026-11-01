package hsu.hanseomate.domain.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.notification.entity.Notification;
import hsu.hanseomate.domain.notification.repository.NotificationRepository;
import hsu.hanseomate.domain.push.entity.NotificationOutbox;
import hsu.hanseomate.domain.push.repository.NotificationOutboxRepository;
import hsu.hanseomate.domain.push.service.NotificationService;
import hsu.hanseomate.domain.push.type.NotificationCategory;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class NotificationCategoryPayloadTest {
    @ParameterizedTest
    @EnumSource(NotificationCategory.class)
    void distinguishesAllSixCategoriesInPushAndInbox(NotificationCategory category) throws Exception {
        var outboxes = mock(NotificationOutboxRepository.class);
        var notifications = mock(NotificationRepository.class);
        var mapper = new ObjectMapper();
        var service = new NotificationService(outboxes, notifications, mapper);
        var startsAt = OffsetDateTime.parse("2026-10-14T18:00:00+09:00");
        switch (category) {
            case NOTICE_MILESTONE -> service.enqueueNoticeNotification("공지", "내용", "10");
            case STUDENT_COUNCIL_NOTICE -> service.enqueueStudentCouncilNoticeNotification("학생회", "내용", "10");
            case SYSTEM_NOTICE -> service.enqueueSystemNoticeNotification("시스템", "내용", "10");
            case CLUB_RECRUITMENT -> service.enqueueClubRecruitmentNotification("동아리", "내용", "10", List.of(7L));
            case CLASS_REMINDER -> service.enqueueTimetableClassReminder(7L, 10L, 11L, 2026, 2, "수업", startsAt);
            case SHUTTLE_RESERVATION -> service.enqueueShuttleReservationReminder(startsAt);
        }
        var outbox = ArgumentCaptor.forClass(NotificationOutbox.class);
        var notification = ArgumentCaptor.forClass(Notification.class);
        verify(outboxes).save(outbox.capture());
        verify(notifications).save(notification.capture());
        var pushData = mapper.readTree(outbox.getValue().getPayload()).get("data");
        assertThat(pushData.get("category").asText()).isEqualTo(category.name());
        assertThat(mapper.readTree(notification.getValue().getPayloadData())).isEqualTo(pushData);
        String expectedType = switch (category) {
            case NOTICE_MILESTONE, STUDENT_COUNCIL_NOTICE -> "notice";
            case SYSTEM_NOTICE -> "system_notice";
            case CLUB_RECRUITMENT -> "club_recruitment";
            case CLASS_REMINDER -> "schedule";
            case SHUTTLE_RESERVATION -> "shuttle_reservation";
        };
        assertThat(pushData.get("type").asText()).isEqualTo(expectedType);
        Long expectedRecipient = category == NotificationCategory.CLUB_RECRUITMENT
                || category == NotificationCategory.CLASS_REMINDER ? 7L : null;
        assertThat(outbox.getValue().getTargetUserId()).isEqualTo(expectedRecipient);
        assertThat(notification.getValue().getTargetUserId()).isEqualTo(expectedRecipient);
    }
}
