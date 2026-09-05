package hsu.hanseomate.domain.timetable.composition.repository;

import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimetableCourseRepository extends JpaRepository<TimetableCourse, Long> {

    @EntityGraph(attributePaths = {
            "timetable", "courseOffering", "courseOffering.course",
            "courseOffering.course.generalEducation"
    })
    @Query("""
            select tc from TimetableCourse tc
            left join tc.courseOffering offering
            where tc.id > :afterId
              and tc.timetable.academicYear = :academicYear
              and tc.timetable.semester = :semester
              and (
                (tc.courseOffering is null and tc.customDayOfWeek = :dayOfWeek)
                or (offering.active = true and exists (
                    select schedule.id from CourseSchedule schedule
                    where schedule.course = offering.course
                      and schedule.dayOfWeek = :dayOfWeek
                ))
              )
            order by tc.id
            """)
    List<TimetableCourse> findReminderEntries(
            @Param("academicYear") int academicYear,
            @Param("semester") int semester,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("afterId") long afterId,
            Pageable pageable
    );

    boolean existsByTimetableIdAndCourseOfferingId(Long timetableId, UUID courseOfferingId);

    @EntityGraph(attributePaths = {
            "courseOffering",
            "courseOffering.course",
            "courseOffering.course.generalEducation"
    })
    List<TimetableCourse> findAllByTimetableIdOrderById(Long timetableId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "courseOffering",
            "courseOffering.course",
            "courseOffering.course.generalEducation"
    })
    @Query("""
            select tc
            from TimetableCourse tc
            where tc.timetable.id = :timetableId
            order by tc.id
            """)
    List<TimetableCourse> findAllByTimetableIdForGradeReset(
            @Param("timetableId") Long timetableId
    );

    @EntityGraph(attributePaths = {
            "timetable",
            "courseOffering",
            "courseOffering.course",
            "courseOffering.course.generalEducation"
    })
    @Query("""
            select tc
            from TimetableCourse tc
            where tc.timetable.ownerId = :ownerId
            order by tc.timetable.academicYear desc,
                     tc.timetable.semester desc,
                     tc.id
            """)
    List<TimetableCourse> findAllByOwnerIdForGradeCalculation(
            @Param("ownerId") Long ownerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select tc
            from TimetableCourse tc
            join fetch tc.timetable timetable
            where tc.id = :timetableCourseId
              and timetable.ownerId = :ownerId
            """)
    Optional<TimetableCourse> findOwnedByIdForUpdate(
            @Param("timetableCourseId") Long timetableCourseId,
            @Param("ownerId") Long ownerId
    );

    @Query("select tc.timetable.id from TimetableCourse tc where tc.id = :timetableCourseId")
    Optional<Long> findTimetableIdById(
            @Param("timetableCourseId") Long timetableCourseId
    );

    @Modifying
    @Query("""
            delete from TimetableCourse tc
            where tc.id = :timetableCourseId
              and tc.timetable.id = :timetableId
            """)
    int deleteByIdAndTimetableId(
            @Param("timetableCourseId") Long timetableCourseId,
            @Param("timetableId") Long timetableId
    );

    @Modifying
    @Query("delete from TimetableCourse tc where tc.timetable.id = :timetableId")
    int deleteAllByTimetableId(@Param("timetableId") Long timetableId);

    @Modifying
    @Query("""
            delete from TimetableCourse tc
            where tc.timetable.id in (
                select timetable.id
                from Timetable timetable
                where timetable.ownerId = :ownerId
            )
            """)
    int deleteAllByTimetableOwnerId(@Param("ownerId") Long ownerId);
}
