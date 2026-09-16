package hsu.hanseomate.domain.notification.service;

import hsu.hanseomate.domain.notification.dto.NotificationResponse;
import hsu.hanseomate.domain.notification.dto.UnreadCountResponse;
import hsu.hanseomate.domain.notification.entity.Notification;
import hsu.hanseomate.domain.notification.entity.NotificationRead;
import hsu.hanseomate.domain.notification.repository.NotificationReadRepository;
import hsu.hanseomate.domain.notification.repository.NotificationRepository;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private static final int MAX_NOTIFICATIONS = 20;

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository notificationReadRepository;
    private final PushDeviceRepository pushDeviceRepository;

    /**
     * 알림 목록 조회 (최신 20개 범위 내에서 페이지 처리)
     * page >= 2 이면 빈 리스트 반환 (size=10 기준 20개 이상은 없음)
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String installationId, int page, int size) {
        List<Notification> top20 = findVisibleNotifications(installationId);

        if (top20.isEmpty()) {
            return List.of();
        }

        int fromIndex = page * size;
        if (fromIndex >= MAX_NOTIFICATIONS || fromIndex >= top20.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + size, Math.min(MAX_NOTIFICATIONS, top20.size()));
        List<Notification> pageSlice = top20.subList(fromIndex, toIndex);

        Set<Long> readIds = notificationReadRepository.findReadNotificationIds(installationId, pageSlice);

        return pageSlice.stream()
                .map(n -> new NotificationResponse(
                        n.getId(),
                        n.getTitle(),
                        n.getBody(),
                        n.getPayloadData(),
                        readIds.contains(n.getId()),
                        n.getCreatedAt()
                ))
                .toList();
    }

    /**
     * 미확인 알림 카운트 — 최신 20개 중 읽지 않은 건수
     */
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(String installationId) {
        List<Notification> top20 = findVisibleNotifications(installationId);

        if (top20.isEmpty()) {
            return new UnreadCountResponse(0);
        }

        Set<Long> readIds = notificationReadRepository.findReadNotificationIds(installationId, top20);
        long unread = top20.stream()
                .filter(n -> !readIds.contains(n.getId()))
                .count();

        return new UnreadCountResponse(unread);
    }

    /**
     * 단건 읽음 처리 — 이미 읽은 경우 중복 저장 방지
     */
    @Transactional
    public void markAsRead(Long notificationId, String installationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("알림을 찾을 수 없습니다. id=" + notificationId));
        Long currentUserId = resolveUserId(installationId);
        if (notification.getTargetUserId() != null
                && !Objects.equals(notification.getTargetUserId(), currentUserId)) {
            throw new ResourceNotFoundException("알림을 찾을 수 없습니다. id=" + notificationId);
        }

        boolean alreadyRead = notificationReadRepository
                .existsByInstallationIdAndNotification(installationId, notification);

        if (!alreadyRead) {
            notificationReadRepository.save(
                    NotificationRead.builder()
                            .installationId(installationId)
                            .notification(notification)
                            .build()
            );
        }
    }

    /**
     * 전체 읽음 처리 — 최신 20개 중 아직 읽지 않은 항목 벌크 INSERT
     */
    @Transactional
    public void markAllAsRead(String installationId) {
        List<Notification> top20 = findVisibleNotifications(installationId);

        if (top20.isEmpty()) {
            return;
        }

        Set<Long> readIds = notificationReadRepository.findReadNotificationIds(installationId, top20);

        List<NotificationRead> toInsert = new ArrayList<>();
        for (Notification n : top20) {
            if (!readIds.contains(n.getId())) {
                toInsert.add(NotificationRead.builder()
                        .installationId(installationId)
                        .notification(n)
                        .build());
            }
        }

        if (!toInsert.isEmpty()) {
            notificationReadRepository.saveAll(toInsert);
        }
    }

    private List<Notification> findVisibleNotifications(String installationId) {
        return notificationRepository.findTop20VisibleToUserOrderByCreatedAtDesc(
                resolveUserId(installationId)
        );
    }

    private Long resolveUserId(String installationId) {
        return pushDeviceRepository.findByInstallationId(installationId)
                .map(PushDevice::getUserId)
                .orElse(null);
    }
}
