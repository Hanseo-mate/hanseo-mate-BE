package hsu.hanseomate.domain.courseenrichment.crossmajor.repository;

import hsu.hanseomate.domain.courseenrichment.crossmajor.entity.CrossMajorRuleContent;
import hsu.hanseomate.domain.courseenrichment.crossmajor.type.CrossMajorRecognitionImportStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrossMajorRuleContentRepository
        extends JpaRepository<CrossMajorRuleContent, String> {

    @Query("""
            select membership.content from CrossMajorRuleMembership membership
            where membership.importHistory.policyYear = :policyYear
              and membership.importHistory.status = :status
              and membership.content.courseNameKey = :courseNameKey
            """)
    List<CrossMajorRuleContent> findActiveCandidatesByCourseName(
            @Param("policyYear") int policyYear,
            @Param("courseNameKey") String courseNameKey,
            @Param("status") CrossMajorRecognitionImportStatus status
    );
}
