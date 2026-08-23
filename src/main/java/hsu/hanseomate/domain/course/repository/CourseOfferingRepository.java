package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOfferingRepository
        extends JpaRepository<CourseOffering, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<CourseOffering> {

    @EntityGraph(attributePaths = {
            "semester",
            "course",
            "course.academicUnit",
            "course.generalEducation"
    })
    @Query("select o from CourseOffering o where o.id = :id and o.active = true")
    Optional<CourseOffering> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"course"})
    List<CourseOffering> findAllBySemesterId(UUID semesterId);

    @EntityGraph(attributePaths = {"course"})
    List<CourseOffering> findAllBySemesterIdAndScopeCurriculumType(
            UUID semesterId,
            CurriculumType scopeCurriculumType
    );

    @Query("""
            select distinct o.importHistory from CourseOffering o
            where o.semester.id = :semesterId
              and o.scopeCurriculumType = :curriculumType
              and o.active = true
            """)
    List<CourseImportHistory> findImportHistoriesByScope(
            @Param("semesterId") UUID semesterId,
            @Param("curriculumType") CurriculumType curriculumType
    );

    @Override
    @EntityGraph(attributePaths = {
            "semester",
            "course",
            "course.academicUnit",
            "course.generalEducation"
    })
    List<CourseOffering> findAll();

    @Override
    @EntityGraph(attributePaths = {
            "semester",
            "course",
            "course.academicUnit",
            "course.generalEducation"
    })
    Page<CourseOffering> findAll(
            Specification<CourseOffering> specification,
            Pageable pageable
    );
}
