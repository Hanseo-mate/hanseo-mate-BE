package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, UUID> {

    @Query("""
            select o.id from CourseOffering o
            where o.semester.id = :semesterId and o.curriculumType = :curriculumType
            """)
    List<UUID> findIdsByScope(
            @Param("semesterId") UUID semesterId,
            @Param("curriculumType") CurriculumType curriculumType
    );

    @Modifying
    @Query("delete from CourseOffering o where o.id in :ids")
    int deleteAllByIdIn(@Param("ids") Collection<UUID> ids);

    @Query("""
            select distinct o from CourseOffering o
            join fetch o.semester s
            join fetch o.course c
            left join fetch o.academicUnit au
            left join fetch o.generalEducation ge
            where (:academicYear is null or s.academicYear = :academicYear)
              and (:semester is null or s.semester = :semester)
              and (:curriculumType is null or o.curriculumType = :curriculumType)
              and (:academicUnit is null or lower(au.originalName) like lower(concat('%', :academicUnit, '%'))
                   or lower(au.departmentName) like lower(concat('%', :academicUnit, '%'))
                   or lower(au.majorName) like lower(concat('%', :academicUnit, '%')))
              and (:classification is null or ge.classification = :classification)
              and (:area is null or ge.area = :area)
              and (:deliveryProvider is null or ge.deliveryProvider = :deliveryProvider)
              and (:courseName is null or lower(o.courseName) like lower(concat('%', :courseName, '%')))
              and (:instructorName is null or lower(o.instructorName) like lower(concat('%', :instructorName, '%')))
            order by s.academicYear desc, s.semester desc, o.courseCode asc, o.sectionNo asc, o.id asc
            """)
    List<CourseOffering> search(
            @Param("academicYear") Integer academicYear,
            @Param("semester") Integer semester,
            @Param("curriculumType") CurriculumType curriculumType,
            @Param("academicUnit") String academicUnit,
            @Param("classification") GeneralClassification classification,
            @Param("area") GeneralArea area,
            @Param("deliveryProvider") DeliveryProvider deliveryProvider,
            @Param("courseName") String courseName,
            @Param("instructorName") String instructorName
    );
}
