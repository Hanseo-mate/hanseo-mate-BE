package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.SemesterGeneralCategoryNode;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SemesterGeneralCategoryNodeRepository
        extends JpaRepository<SemesterGeneralCategoryNode, UUID> {

    @Modifying
    @Query("""
            delete from SemesterGeneralCategoryNode n
            where n.semester.id = :semesterId and n.curriculumType = :curriculumType
            """)
    int deleteByScope(
            @Param("semesterId") UUID semesterId,
            @Param("curriculumType") CurriculumType curriculumType
    );

    @Query("""
            select n from SemesterGeneralCategoryNode n
            where n.semester.id = :semesterId and n.curriculumType = :curriculumType
            order by n.sortOrder, n.id
            """)
    List<SemesterGeneralCategoryNode> findByScope(
            @Param("semesterId") UUID semesterId,
            @Param("curriculumType") CurriculumType curriculumType
    );
}
