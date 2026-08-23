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
    @Query("delete from CourseSchedule s where s.course.id in :courseIds")
    int deleteByCourseIds(@Param("courseIds") Collection<UUID> courseIds);

    @Query("""
            select s from CourseSchedule s
            left join fetch s.classroom
            where s.course.id in :courseIds
            order by s.course.id, s.scheduleOrder
            """)
    List<CourseSchedule> findAllForCourses(@Param("courseIds") Collection<UUID> courseIds);

    @Query("""
            select schedule
            from CourseSchedule schedule
            join fetch schedule.course course
            left join fetch schedule.classroom
            where schedule.dayOfWeek = :dayOfWeek
              and exists (
                  select timetableCourse.id
                  from TimetableCourse timetableCourse
                  where timetableCourse.courseOffering.course = course
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
