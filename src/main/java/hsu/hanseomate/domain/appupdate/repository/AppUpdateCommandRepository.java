package hsu.hanseomate.domain.appupdate.repository;

import hsu.hanseomate.domain.appupdate.entity.AppUpdateCommand;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface AppUpdateCommandRepository extends JpaRepository<AppUpdateCommand, String> {
    @Modifying
    @Query(value = """
            INSERT INTO app_update_commands (idempotency_key, fingerprint, created_at)
            VALUES (:key, :fingerprint, :now)
            ON DUPLICATE KEY UPDATE idempotency_key = :key
            """, nativeQuery = true)
    void reserve(String key, String fingerprint, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AppUpdateCommand c where c.idempotencyKey = :key")
    Optional<AppUpdateCommand> findForUpdate(String key);
}
