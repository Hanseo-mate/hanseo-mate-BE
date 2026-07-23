package hsu.hanseomate.domain.course.entity;

import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "course_offerings")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_unit_id")
    private AcademicUnit academicUnit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_history_id", nullable = false)
    private CourseImportHistory importHistory;

    @Enumerated(EnumType.STRING)
    @Column(name = "curriculum_type", nullable = false, length = 30)
    private CurriculumType curriculumType;

    @Column(name = "source_sheet", nullable = false, length = 255)
    private String sourceSheet;

    @Column(name = "source_row", nullable = false)
    private int sourceRow;

    @Column(name = "section_no", length = 100)
    private String sectionNo;

    @Column(name = "course_code_snapshot", length = 100)
    private String courseCode;

    @Column(name = "course_name_snapshot", length = 255)
    private String courseName;

    @Column(precision = 8, scale = 3)
    private BigDecimal credit;

    @Column(name = "class_hours", precision = 8, scale = 3)
    private BigDecimal classHours;

    @Column(name = "instructor_name", length = 255)
    private String instructorName;

    @Column(name = "target_grade")
    private Integer targetGrade;

    @Column(name = "common_grade", nullable = false)
    private boolean commonGrade;

    @Column(name = "team_teaching")
    private Boolean teamTeaching;

    @Column(length = 2000)
    private String note;

    @Column(name = "eligibility_note", length = 2000)
    private String eligibilityNote;

    @Column(name = "schedule_text", length = 2000)
    private String scheduleText;

    @Column(name = "classroom_text", length = 2000)
    private String classroomText;

    @OneToOne(mappedBy = "offering", fetch = FetchType.LAZY)
    private OfferingGeneralEducation generalEducation;

    private CourseOffering(
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
        this.id = UUID.randomUUID();
        this.semester = semester;
        this.course = course;
        this.academicUnit = academicUnit;
        this.importHistory = importHistory;
        this.curriculumType = curriculumType;
        this.sourceSheet = sourceSheet;
        this.sourceRow = sourceRow;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.sectionNo = sectionNo;
        this.credit = credit;
        this.classHours = classHours;
        this.instructorName = instructorName;
        this.targetGrade = targetGrade;
        this.commonGrade = commonGrade;
        this.teamTeaching = teamTeaching;
        this.note = note;
        this.eligibilityNote = eligibilityNote;
        this.scheduleText = scheduleText;
        this.classroomText = classroomText;
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
        return new CourseOffering(
                semester, course, academicUnit, importHistory, curriculumType,
                sourceSheet, sourceRow, courseCode, courseName, sectionNo,
                credit, classHours, instructorName,
                targetGrade, commonGrade, teamTeaching, note, eligibilityNote,
                scheduleText, classroomText
        );
    }
}
