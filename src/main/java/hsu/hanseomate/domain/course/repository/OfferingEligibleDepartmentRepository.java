package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.OfferingEligibleDepartment;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferingEligibleDepartmentRepository extends JpaRepository<OfferingEligibleDepartment, UUID> {

    @Modifying
    @Query("delete from OfferingEligibleDepartment d where d.offering.id in :offeringIds")
    int deleteByOfferingIds(@Param("offeringIds") Collection<UUID> offeringIds);
}
