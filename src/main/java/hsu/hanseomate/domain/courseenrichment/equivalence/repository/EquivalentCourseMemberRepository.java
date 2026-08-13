package hsu.hanseomate.domain.courseenrichment.equivalence.repository;

import hsu.hanseomate.domain.courseenrichment.equivalence.entity.EquivalentCourseMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquivalentCourseMemberRepository
        extends JpaRepository<EquivalentCourseMember, UUID> {

    @EntityGraph(attributePaths = "group")
    @Query("""
            select member from EquivalentCourseMember member
            where member.importHistory.activeScopeKey = :activeScopeKey
              and member.courseCode = :courseCode
            """)
    Optional<EquivalentCourseMember> findActiveMember(
            @Param("activeScopeKey") String activeScopeKey,
            @Param("courseCode") String courseCode
    );

    List<EquivalentCourseMember> findAllByGroupIdOrderByMemberOrderAsc(UUID groupId);
}
