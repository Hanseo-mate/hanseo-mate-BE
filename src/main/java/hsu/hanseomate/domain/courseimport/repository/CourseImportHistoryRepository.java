package hsu.hanseomate.domain.courseimport.repository;

import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseImportHistoryRepository extends JpaRepository<CourseImportHistory, UUID> {

    Optional<CourseImportHistory> findBySuccessfulDedupKey(String successfulDedupKey);

    Optional<CourseImportHistory> findByImportId(String importId);
}
