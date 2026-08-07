package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOfferingRepository
        extends JpaRepository<CourseOffering, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<CourseOffering> {

    @EntityGraph(attributePaths = {
            "semester",
            "course",
            "academicUnit",
            "generalEducation"
    })
    @Query("select o from CourseOffering o where o.id = :id")
    Optional<CourseOffering> findDetailedById(@Param("id") UUID id);

    @Query("""
            select o.id from CourseOffering o
            where o.semester.id = :semesterId and o.curriculumType = :curriculumType
            """)
    List<UUID> findIdsByScope(
            @Param("semesterId") UUID semesterId,
            @Param("curriculumType") CurriculumType curriculumType
    );

    @Query("""
            select distinct o.importHistory from CourseOffering o
            where o.semester.id = :semesterId and o.curriculumType = :curriculumType
            """)
    List<CourseImportHistory> findImportHistoriesByScope(
            @Param("semesterId") UUID semesterId,
            @Param("curriculumType") CurriculumType curriculumType
    );

    @Modifying
    @Query("delete from CourseOffering o where o.id in :ids")
    int deleteAllByIdIn(@Param("ids") Collection<UUID> ids);

    @Override
    @EntityGraph(attributePaths = {
            "semester",
            "course",
            "academicUnit",
            "generalEducation"
    })
    Page<CourseOffering> findAll(
            Specification<CourseOffering> specification,
            Pageable pageable
    );
}
