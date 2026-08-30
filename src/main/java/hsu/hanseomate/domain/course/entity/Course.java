package hsu.hanseomate.domain.course.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 학기와 무관하게 과목코드와 분반 조합으로 한 번만 저장되는 강좌 데이터입니다.
 *
 * <p>같은 과목코드와 분반 조합이 다시 수입되면 이 엔티티의 최초 저장값을 계속 사용하고,
 * 연도·학기와 수입 출처만 {@link CourseOffering}에 별도로 연결합니다. 같은 과목코드라도
 * 분반이 다르면 교수, 시간, 강의실이 서로 다른 별도 강좌로 저장합니다.
 */
@Getter
@Entity
@Table(
        name = "courses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_course_master_key",
                columnNames = "master_key"
        ),
        indexes = {
                @Index(name = "ix_course_code", columnList = "course_code"),
                @Index(name = "ix_course_name", columnList = "course_name"),
                @Index(name = "ix_course_instructor", columnList = "instructor_name"),
                @Index(name = "ix_course_curriculum", columnList = "curriculum_type")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    private UUID id;

    @Column(name = "master_key", nullable = false, length = 64)
    private String masterKey;

    @Column(name = "course_code", length = 100)
    private String courseCode;

    @Column(name = "course_name", length = 255)
    private String courseName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_unit_id")
    private AcademicUnit academicUnit;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "curriculum_type", length = 30)
    private CurriculumType curriculumType;

    @Column(name = "section_no", length = 100)
    private String sectionNo;

    @Column(precision = 8, scale = 3)
    private BigDecimal credit;

    @Column(name = "class_hours", precision = 8, scale = 3)
    private BigDecimal classHours;

    @Column(name = "instructor_name", length = 255)
    private String instructorName;

    @Column(name = "target_grade")
    private Integer targetGrade;

    @Column(name = "common_grade")
    private Boolean commonGrade;

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

    @OneToOne(mappedBy = "course", fetch = FetchType.LAZY)
    private OfferingGeneralEducation generalEducation;

    private Course(String masterKey, String courseCode, String courseName) {
        this.id = UUID.randomUUID();
        this.masterKey = masterKey;
        this.courseCode = courseCode;
        this.courseName = courseName;
    }

    public static Course create(String masterKey, String courseCode, String courseName) {
        return new Course(masterKey, courseCode, courseName);
    }

    public static Course createWithDetails(
            String masterKey,
            String courseCode,
            String courseName,
            AcademicUnit academicUnit,
            CurriculumType curriculumType,
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
        Course course = new Course(masterKey, courseCode, courseName);
        course.initializeDetailsIfMissing(
                academicUnit, curriculumType, sectionNo, credit, classHours,
                instructorName, targetGrade, commonGrade, teamTeaching, note,
                eligibilityNote, scheduleText, classroomText
        );
        return course;
    }

    /**
     * 코드·이름만 먼저 만들어진 과목을 한 번만 완성합니다.
     * 이미 상세가 저장된 강좌는 과목코드·분반 중복 정책에 따라 최초 값을 유지합니다.
     */
    public boolean initializeDetailsIfMissing(
            AcademicUnit academicUnit,
            CurriculumType curriculumType,
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
        if (this.curriculumType != null) {
            return false;
        }
        this.academicUnit = academicUnit;
        this.curriculumType = curriculumType;
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
        return true;
    }

    public boolean isCommonGrade() {
        return Boolean.TRUE.equals(commonGrade);
    }
}
