package hsu.hanseomate.domain.courseenrichment.equivalence.repository;

import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquivalentCourseMembershipRepository
        extends JpaRepository<EquivalentCourseMembership, UUID> {

    @EntityGraph(attributePaths = {"group", "content"})
    @Query("""
            select member from EquivalentCourseMembership member
            where member.importHistory.activeScopeKey = :activeScopeKey
              and member.courseCode = :courseCode
            """)
    Optional<EquivalentCourseMembership> findActiveMember(
            @Param("activeScopeKey") String activeScopeKey,
            @Param("courseCode") String courseCode
    );

    @EntityGraph(attributePaths = "content")
    List<EquivalentCourseMembership> findAllByGroupIdOrderByMemberOrderAsc(UUID groupId);
}
