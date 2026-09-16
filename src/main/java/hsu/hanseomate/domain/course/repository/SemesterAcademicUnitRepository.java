package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.SemesterAcademicUnit;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemesterAcademicUnitRepository extends JpaRepository<SemesterAcademicUnit, UUID> {

    @Modifying
    @Query("""
            delete from SemesterAcademicUnit s
            where s.semester.id = :semesterId and s.curriculumType = :curriculumType
            """)
    int deleteByScope(
            @Param("semesterId") UUID semesterId,
            @Param("curriculumType") CurriculumType curriculumType
    );
}
