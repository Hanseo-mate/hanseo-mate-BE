package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.OfferingAllowedGrade;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferingAllowedGradeRepository extends JpaRepository<OfferingAllowedGrade, UUID> {

    @Modifying
    @Query("delete from OfferingAllowedGrade g where g.course.id in :courseIds")
    int deleteByCourseIds(@Param("courseIds") Collection<UUID> courseIds);
}
