package hsu.hanseomate.domain.push.repository;

import hsu.hanseomate.domain.push.entity.NotificationOutbox;
import hsu.hanseomate.domain.push.entity.OutboxStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<NotificationOutbox> findAllByStatus(OutboxStatus status);
}
