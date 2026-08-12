package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.OfferingEligibleDepartment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferingEligibleDepartmentRepository extends JpaRepository<OfferingEligibleDepartment, UUID> {

    @Query("""
            select department.offering.id as offeringId,
                   department.departmentName as departmentName
            from OfferingEligibleDepartment department
            where department.offering.id in :offeringIds
            order by department.departmentName asc
            """)
    List<OfferingEligibleDepartmentNameProjection> findNamesByOfferingIds(
            @Param("offeringIds") Collection<UUID> offeringIds
    );

    @Modifying
    @Query("delete from OfferingEligibleDepartment d where d.offering.id in :offeringIds")
    int deleteByOfferingIds(@Param("offeringIds") Collection<UUID> offeringIds);
}
