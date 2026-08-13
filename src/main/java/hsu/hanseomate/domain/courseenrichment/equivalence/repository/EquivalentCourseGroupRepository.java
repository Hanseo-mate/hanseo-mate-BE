package hsu.hanseomate.domain.courseenrichment.equivalence.repository;

import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseGroup;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquivalentCourseGroupRepository
        extends JpaRepository<EquivalentCourseGroup, UUID> {
}
