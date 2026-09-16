package hsu.hanseomate.domain.notification.repository;

import hsu.hanseomate.domain.notification.entity.Notification;
import hsu.hanseomate.domain.notification.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {

    boolean existsByInstallationIdAndNotification(String installationId, Notification notification);

    Optional<NotificationRead> findByInstallationIdAndNotification(String installationId, Notification notification);

    /** 특정 installationId가 읽은 notificationId 집합 조회 */
    @Query("SELECT nr.notification.id FROM NotificationRead nr " +
           "WHERE nr.installationId = :installationId AND nr.notification IN :notifications")
    Set<Long> findReadNotificationIds(
            @Param("installationId") String installationId,
            @Param("notifications") List<Notification> notifications
    );
}
