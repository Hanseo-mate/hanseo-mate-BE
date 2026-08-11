package hsu.hanseomate.domain.push.repository;

import hsu.hanseomate.domain.push.entity.PushDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByInstallationId(String installationId);

    Optional<PushDevice> findByExpoPushToken(String expoPushToken);

    List<PushDevice> findAllByIsActiveTrue();
}
