package hsu.hanseomate.domain.courseenrichment.crossmajor.repository;

import hsu.hanseomate.domain.courseenrichment.crossmajor.entity.CrossMajorRecognitionImportHistory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrossMajorRecognitionImportHistoryRepository
        extends JpaRepository<CrossMajorRecognitionImportHistory, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select history from CrossMajorRecognitionImportHistory history
            where history.activeScopeKey = :activeScopeKey
            """)
    Optional<CrossMajorRecognitionImportHistory> findActiveForUpdate(
            @Param("activeScopeKey") String activeScopeKey
    );
}
