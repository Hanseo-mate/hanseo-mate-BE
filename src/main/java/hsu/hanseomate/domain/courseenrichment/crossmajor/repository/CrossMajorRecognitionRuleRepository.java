package hsu.hanseomate.domain.courseenrichment.crossmajor.repository;

import hsu.hanseomate.domain.courseenrichment.crossmajor.entity.CrossMajorRecognitionRule;
import hsu.hanseomate.domain.courseenrichment.crossmajor.type.CrossMajorRecognitionImportStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrossMajorRecognitionRuleRepository
        extends JpaRepository<CrossMajorRecognitionRule, UUID> {

    @Query("""
            select rule from CrossMajorRecognitionRule rule
            where rule.importHistory.policyYear = :policyYear
              and rule.importHistory.status = :status
              and rule.courseCode = :courseCode
            """)
    List<CrossMajorRecognitionRule> findActiveCandidates(
            @Param("policyYear") int policyYear,
            @Param("courseCode") String courseCode,
            @Param("status") CrossMajorRecognitionImportStatus status
    );
}
