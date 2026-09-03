package hsu.hanseomate.domain.push.repository;

import hsu.hanseomate.domain.push.entity.PushDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByInstallationId(String installationId);

    Optional<PushDevice> findByExpoPushToken(String expoPushToken);

    List<PushDevice> findAllByIsActiveTrue();

    List<PushDevice> findAllByUserIdAndIsActiveTrue(Long userId);

    @Query("select device.id from PushDevice device where device.userId = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from PushDevice device where device.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
