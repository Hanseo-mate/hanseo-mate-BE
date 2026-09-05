package hsu.hanseomate.domain.appsetting.repository;

import hsu.hanseomate.domain.appsetting.entity.AppFeatureSetting;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppFeatureSettingRepository extends JpaRepository<AppFeatureSetting, String> {

    // 최초 PATCH 간의 생성 경합도 DB의 PK 잠금으로 직렬화한다.
    // 중복 행의 상태/수정 시각은 건드리지 않는다. MySQL 및 H2 MySQL 모드에서 사용한다.
    @Modifying
    @Query(value = """
            INSERT INTO app_feature_settings (setting_key, enabled)
            VALUES (:key, false)
            ON DUPLICATE KEY UPDATE setting_key = :key
            """, nativeQuery = true)
    void ensureDefaultExists(@Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select setting from AppFeatureSetting setting where setting.key = :key")
    Optional<AppFeatureSetting> findByKeyForUpdate(@Param("key") String key);
}
