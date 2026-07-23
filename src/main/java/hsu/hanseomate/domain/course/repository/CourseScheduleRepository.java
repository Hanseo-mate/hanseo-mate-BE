package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.CourseSchedule;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseScheduleRepository extends JpaRepository<CourseSchedule, UUID> {

    @Modifying
    @Query("delete from CourseSchedule s where s.offering.id in :offeringIds")
    int deleteByOfferingIds(@Param("offeringIds") Collection<UUID> offeringIds);

    @Query("""
            select s from CourseSchedule s
            left join fetch s.classroom
            where s.offering.id in :offeringIds
            order by s.offering.id, s.scheduleOrder
            """)
    List<CourseSchedule> findAllForOfferings(@Param("offeringIds") Collection<UUID> offeringIds);
}
