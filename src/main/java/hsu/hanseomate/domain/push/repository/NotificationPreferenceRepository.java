package hsu.hanseomate.domain.push.repository;

import hsu.hanseomate.domain.push.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {
}
