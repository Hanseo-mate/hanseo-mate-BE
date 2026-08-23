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
            select department.course.id as courseId,
                   department.departmentName as departmentName
            from OfferingEligibleDepartment department
            where department.course.id in :courseIds
            order by department.departmentName asc
            """)
    List<OfferingEligibleDepartmentNameProjection> findNamesByCourseIds(
            @Param("courseIds") Collection<UUID> courseIds
    );

    @Modifying
    @Query("delete from OfferingEligibleDepartment d where d.course.id in :courseIds")
    int deleteByCourseIds(@Param("courseIds") Collection<UUID> courseIds);
}
