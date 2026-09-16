package hsu.hanseomate.domain.courseenrichment.equivalence.repository;

import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseImportHistory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquivalentCourseImportHistoryRepository
        extends JpaRepository<EquivalentCourseImportHistory, UUID> {

    Optional<EquivalentCourseImportHistory> findByImportId(String importId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select history from EquivalentCourseImportHistory history
            where history.activeScopeKey = :activeScopeKey
            """)
    Optional<EquivalentCourseImportHistory> findActiveForUpdate(
            @Param("activeScopeKey") String activeScopeKey
    );
}
