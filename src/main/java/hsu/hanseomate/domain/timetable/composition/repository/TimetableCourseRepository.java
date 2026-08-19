package hsu.hanseomate.domain.timetable.composition.repository;

import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimetableCourseRepository extends JpaRepository<TimetableCourse, Long> {

    boolean existsByTimetableIdAndCourseOfferingId(Long timetableId, UUID courseOfferingId);

    @EntityGraph(attributePaths = {
            "courseOffering",
            "courseOffering.generalEducation"
    })
    List<TimetableCourse> findAllByTimetableIdOrderById(Long timetableId);

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
