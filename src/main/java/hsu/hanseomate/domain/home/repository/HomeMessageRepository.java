package hsu.hanseomate.domain.home.repository;

import hsu.hanseomate.domain.home.entity.HomeMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeMessageRepository extends JpaRepository<HomeMessage, Long> {
}
