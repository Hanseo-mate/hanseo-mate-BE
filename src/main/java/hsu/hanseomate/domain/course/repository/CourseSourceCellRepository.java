package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.CourseSourceCell;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseSourceCellRepository extends JpaRepository<CourseSourceCell, UUID> {

    @Modifying
    @Query("delete from CourseSourceCell c where c.offering.id in :offeringIds")
    int deleteByOfferingIds(@Param("offeringIds") Collection<UUID> offeringIds);
}
