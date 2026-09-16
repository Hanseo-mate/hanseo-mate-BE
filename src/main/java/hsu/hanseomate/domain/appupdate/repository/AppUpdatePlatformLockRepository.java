package hsu.hanseomate.domain.appupdate.repository;

import hsu.hanseomate.domain.appupdate.entity.AppUpdatePlatformLock;
import org.springframework.data.jpa.repository.*;

public interface AppUpdatePlatformLockRepository extends JpaRepository<AppUpdatePlatformLock, String> {
    // PK upsert가 트랜잭션 종료까지 행 잠금을 보유한다. 최초 정책 생성 경합도 같은 방식으로 직렬화한다.
    @Modifying
    @Query(value = """
            INSERT INTO app_update_platform_locks (platform) VALUES (:platform)
            ON DUPLICATE KEY UPDATE platform = :platform
            """, nativeQuery = true)
    void acquire(String platform);
}
