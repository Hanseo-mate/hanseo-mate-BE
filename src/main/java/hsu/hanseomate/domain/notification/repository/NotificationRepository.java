package hsu.hanseomate.domain.notification.repository;

import hsu.hanseomate.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 최신 20개 Notification을 최신순으로 조회 */
    @Query("SELECT n FROM Notification n ORDER BY n.createdAt DESC LIMIT 20")
    List<Notification> findTop20ByOrderByCreatedAtDesc();
}
