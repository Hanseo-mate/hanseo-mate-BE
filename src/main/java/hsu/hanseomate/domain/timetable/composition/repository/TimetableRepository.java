package hsu.hanseomate.domain.timetable.composition.repository;

import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    boolean existsByOwnerIdAndAcademicYearAndSemester(
            Long ownerId,
            int academicYear,
            int semester
    );

    Optional<Timetable> findByOwnerIdAndAcademicYearAndSemester(
            Long ownerId,
            int academicYear,
            int semester
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Timetable t where t.id = :timetableId")
    Optional<Timetable> findByIdForUpdate(@Param("timetableId") Long timetableId);
}
