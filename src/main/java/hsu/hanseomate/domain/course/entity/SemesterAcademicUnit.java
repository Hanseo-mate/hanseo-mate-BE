package hsu.hanseomate.domain.course.entity;

import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "semester_academic_units", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_semester_unit_curriculum",
                columnNames = {"semester_id", "academic_unit_id", "curriculum_type"}
        )
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SemesterAcademicUnit {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_unit_id", nullable = false)
    private AcademicUnit academicUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "curriculum_type", nullable = false, length = 30)
    private CurriculumType curriculumType;

    private SemesterAcademicUnit(
            Semester semester,
            AcademicUnit academicUnit,
            CurriculumType curriculumType
    ) {
        this.id = UUID.randomUUID();
        this.semester = semester;
        this.academicUnit = academicUnit;
        this.curriculumType = curriculumType;
    }

    public static SemesterAcademicUnit create(
            Semester semester,
            AcademicUnit academicUnit,
            CurriculumType curriculumType
    ) {
        return new SemesterAcademicUnit(semester, academicUnit, curriculumType);
    }
}
