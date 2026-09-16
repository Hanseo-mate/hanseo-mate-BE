package hsu.hanseomate.domain.courseenrichment.equivalence.repository;

import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseContent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquivalentCourseContentRepository
        extends JpaRepository<EquivalentCourseContent, String> {
}
