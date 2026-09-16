package hsu.hanseomate.domain.course.entity;

import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "course_offerings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_offering_semester_course",
                columnNames = {"semester_id", "course_id"}
        ),
        indexes = {
                @Index(
                        name = "ix_offering_scope",
                        columnList = "semester_id,curriculum_type,active"
                ),
                @Index(name = "ix_offering_import", columnList = "import_history_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseOffering {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_history_id", nullable = false)
    private CourseImportHistory importHistory;

    /** 수입 범위를 구분하기 위한 학기 매핑 메타데이터입니다. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "curriculum_type", nullable = false, length = 30)
    private CurriculumType scopeCurriculumType;

    @Column(name = "source_sheet", nullable = false, length = 255)
    private String sourceSheet;

    @Column(name = "source_row", nullable = false)
    private int sourceRow;

    @Column(nullable = false)
    private boolean active;

    private CourseOffering(
            Semester semester,
            Course course,
            CourseImportHistory importHistory,
            CurriculumType scopeCurriculumType,
            String sourceSheet,
            int sourceRow
    ) {
        this.id = UUID.randomUUID();
        this.semester = semester;
        this.course = course;
        this.importHistory = importHistory;
        this.scopeCurriculumType = scopeCurriculumType;
        this.sourceSheet = sourceSheet;
        this.sourceRow = sourceRow;
        this.active = true;
    }

    public static CourseOffering link(
            Semester semester,
            Course course,
            CourseImportHistory importHistory,
            CurriculumType scopeCurriculumType,
            String sourceSheet,
            int sourceRow
    ) {
        return new CourseOffering(
                semester,
                course,
                importHistory,
                scopeCurriculumType,
                sourceSheet,
                sourceRow
        );
    }

    public static CourseOffering create(
            Semester semester,
            Course course,
            AcademicUnit academicUnit,
            CourseImportHistory importHistory,
            CurriculumType curriculumType,
            String sourceSheet,
            int sourceRow,
            String courseCode,
            String courseName,
            String sectionNo,
            BigDecimal credit,
            BigDecimal classHours,
            String instructorName,
            Integer targetGrade,
            boolean commonGrade,
            Boolean teamTeaching,
            String note,
            String eligibilityNote,
            String scheduleText,
            String classroomText
    ) {
        course.initializeDetailsIfMissing(
                academicUnit, curriculumType, sectionNo, credit, classHours,
                instructorName, targetGrade, commonGrade, teamTeaching, note,
                eligibilityNote, scheduleText, classroomText
        );
        return link(
                semester,
                course,
                importHistory,
                curriculumType,
                sourceSheet,
                sourceRow
        );
    }

    public void refreshImportSource(
            CourseImportHistory importHistory,
            CurriculumType scopeCurriculumType,
            String sourceSheet,
            int sourceRow
    ) {
        this.importHistory = importHistory;
        this.scopeCurriculumType = scopeCurriculumType;
        this.sourceSheet = sourceSheet;
        this.sourceRow = sourceRow;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public AcademicUnit getAcademicUnit() {
        return course.getAcademicUnit();
    }

    public CurriculumType getCurriculumType() {
        return course.getCurriculumType();
    }

    public String getSectionNo() {
        return course.getSectionNo();
    }

    public String getCourseCode() {
        return course.getCourseCode();
    }

    public String getCourseName() {
        return course.getCourseName();
    }

    public BigDecimal getCredit() {
        return course.getCredit();
    }

    public BigDecimal getClassHours() {
        return course.getClassHours();
    }

    public String getInstructorName() {
        return course.getInstructorName();
    }

    public Integer getTargetGrade() {
        return course.getTargetGrade();
    }

    public boolean isCommonGrade() {
        return course.isCommonGrade();
    }

    public Boolean getTeamTeaching() {
        return course.getTeamTeaching();
    }

    public String getNote() {
        return course.getNote();
    }

    public String getEligibilityNote() {
        return course.getEligibilityNote();
    }

    public String getScheduleText() {
        return course.getScheduleText();
    }

    public String getClassroomText() {
        return course.getClassroomText();
    }

    public OfferingGeneralEducation getGeneralEducation() {
        return course.getGeneralEducation();
    }
}
