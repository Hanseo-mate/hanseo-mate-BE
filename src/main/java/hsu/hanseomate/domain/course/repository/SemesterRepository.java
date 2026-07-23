package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.Semester;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemesterRepository extends JpaRepository<Semester, UUID> {

    Optional<Semester> findByAcademicYearAndSemester(int academicYear, int semester);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Semester s where s.academicYear = :year and s.semester = :semester")
    Optional<Semester> findForUpdate(
            @Param("year") int academicYear,
            @Param("semester") int semester
    );
}
