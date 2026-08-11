package hsu.hanseomate.domain.cafeteria.repository;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyMenuRepository extends JpaRepository<DailyMenu, Long>, DailyMenuRepositoryCustom {
}
