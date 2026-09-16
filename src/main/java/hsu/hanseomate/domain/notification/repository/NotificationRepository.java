package hsu.hanseomate.domain.notification.repository;

import hsu.hanseomate.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 전체 알림과 현재 로그인 사용자 대상 알림을 합쳐 최신 20건 조회합니다. */
    @Query("""
            SELECT n
            FROM Notification n
            WHERE n.targetUserId IS NULL OR n.targetUserId = :targetUserId
            ORDER BY n.createdAt DESC
            LIMIT 20
            """)
    List<Notification> findTop20VisibleToUserOrderByCreatedAtDesc(
            @Param("targetUserId") Long targetUserId
    );
}
