package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
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

    @Query("""
            select schedule
            from CourseSchedule schedule
            join fetch schedule.offering offering
            left join fetch schedule.classroom
            where schedule.dayOfWeek = :dayOfWeek
              and exists (
                  select timetableCourse.id
                  from TimetableCourse timetableCourse
                  where timetableCourse.courseOffering = offering
                    and timetableCourse.timetable.ownerId = :ownerId
                    and timetableCourse.timetable.academicYear = :academicYear
                    and timetableCourse.timetable.semester = :semester
              )
            """)
    List<CourseSchedule> findHomeSchedules(
            @Param("ownerId") Long ownerId,
            @Param("academicYear") int academicYear,
            @Param("semester") int semester,
            @Param("dayOfWeek") DayOfWeek dayOfWeek
    );
}
