package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.AcademicUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicUnitRepository extends JpaRepository<AcademicUnit, UUID> {

    List<AcademicUnit> findAllByMasterKeyIn(Collection<String> masterKeys);
}
