package hsu.hanseomate.domain.courseenrichment.crossmajor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hsu.hanseomate.domain.course.entity.AcademicUnit;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.Semester;
import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionResponse;
import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionRuleData;
import hsu.hanseomate.domain.courseenrichment.crossmajor.entity.CrossMajorRecognitionImportHistory;
import hsu.hanseomate.domain.courseenrichment.crossmajor.entity.CrossMajorRecognitionRule;
import hsu.hanseomate.domain.courseenrichment.crossmajor.repository.CrossMajorRecognitionRuleRepository;
import hsu.hanseomate.domain.courseenrichment.crossmajor.support.CrossMajorRecognitionNormalizer;
import hsu.hanseomate.domain.courseenrichment.crossmajor.type.CrossMajorRecognitionImportStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrossMajorRecognitionQueryServiceTest {

    private CrossMajorRecognitionRuleRepository repository;
    private CrossMajorRecognitionQueryService service;
    private CrossMajorRecognitionImportHistory history;

    @BeforeEach
    void setUp() {
        repository = mock(CrossMajorRecognitionRuleRepository.class);
        service = new CrossMajorRecognitionQueryService(repository);
        history = CrossMajorRecognitionImportHistory.active(
                2026, 1, "CROSS_MAJOR:2026", "rules.xlsx",
                "a".repeat(64), "b".repeat(64), "rules", 1, 1, 0, "[]", "[]"
        );
    }

    @Test
    void matchesActiveAnnualCodeOrganizationAndEffectivePeriodThenSortsDistinct() {
        CourseOffering offering = offering(
                2026,
                1,
                "4436",
                "자료 구조",
                "개설 학과",
                "개설 전공"
        );
        List<CrossMajorRecognitionRule> candidates = List.of(
                rule("학생대학", "나학과", "나전공", "개설학과", "개설전공",
                        "0004436", "자료구조", 2020, 1, 2),
                rule("학생대학", "가학과", "가전공", "개설학과", "개설전공",
                        "0004436", "자료구조", 2020, 1, 3),
                rule("학생대학", "가학과", "가전공", "개설학과", "개설전공",
                        "0004436", "자료구조", 2020, 1, 4),
                rule("학생대학", "미래학과", "미래전공", "개설학과", "개설전공",
                        "0004436", "자료구조", 2026, 2, 5)
        );
        when(repository.findActiveCandidates(
                2026, "0004436", CrossMajorRecognitionImportStatus.ACTIVE
        )).thenReturn(candidates);

        List<CrossMajorRecognitionResponse> result = service.findRecognitions(offering);

        assertThat(result).containsExactly(
                new CrossMajorRecognitionResponse("학생대학", "가학과", "가전공", 2020, 1),
                new CrossMajorRecognitionResponse("학생대학", "나학과", "나전공", 2020, 1)
        );
        verify(repository).findActiveCandidates(
                2026, "0004436", CrossMajorRecognitionImportStatus.ACTIVE
        );
    }

    @Test
    void courseNameDisambiguatesSameCodeInsideExactOrganization() {
        CourseOffering offering = offering(
                2026, 2, "0009778", "새 과목", "개설학과", "개설전공"
        );
        when(repository.findActiveCandidates(
                2026, "0009778", CrossMajorRecognitionImportStatus.ACTIVE
        )).thenReturn(List.of(
                rule("학생대학", "이전대상", "이전전공", "개설학과", "개설전공",
                        "0009778", "이전과목", 2020, 1, 2),
                rule("학생대학", "신규대상", "신규전공", "개설학과", "개설전공",
                        "0009778", "새과목", 2021, 1, 3)
        ));

        assertThat(service.findRecognitions(offering)).containsExactly(
                new CrossMajorRecognitionResponse("학생대학", "신규대상", "신규전공", 2021, 1)
        );
    }

    @Test
    void ambiguousExactOrganizationNeverFallsBackToDifferentOrganization() {
        CourseOffering offering = offering(
                2026, 2, "0009778", "다른 조직 과목", "개설학과", "개설전공"
        );
        when(repository.findActiveCandidates(
                2026, "0009778", CrossMajorRecognitionImportStatus.ACTIVE
        )).thenReturn(List.of(
                rule("학생대학", "대상1", "전공1", "개설학과", "개설전공",
                        "0009778", "과목A", 2020, 1, 2),
                rule("학생대학", "대상2", "전공2", "개설학과", "개설전공",
                        "0009778", "과목B", 2020, 1, 3),
                rule("학생대학", "오연결대상", "오연결전공", "다른학과", "다른전공",
                        "0009778", "다른조직과목", 2020, 1, 4)
        ));

        assertThat(service.findRecognitions(offering)).isEmpty();
    }

    @Test
    void neverMatchesByCodeAndCourseNameWhenOfferingOrganizationDiffers() {
        CourseOffering offering = offering(
                2026, 2, "0010529", "정확 과목", "변경된학과", "변경된전공"
        );
        when(repository.findActiveCandidates(
                2026, "0010529", CrossMajorRecognitionImportStatus.ACTIVE
        )).thenReturn(List.of(
                rule("학생대학", "대상1", "전공1", "예전학과", "예전전공",
                        "0010529", "정확과목", 2020, 1, 2),
                rule("학생대학", "대상2", "전공2", "예전학과", "예전전공",
                        "0010529", "다른과목", 2020, 1, 3)
        ));

        assertThat(service.findRecognitions(offering)).isEmpty();
    }

    @Test
    void invalidOfferingIdentityReturnsEmptyWithoutRepositoryLookup() {
        CourseOffering offering = mock(CourseOffering.class);
        when(offering.getCourseCode()).thenReturn("course-name-only");
        when(offering.getSemester()).thenReturn(Semester.create(2026, 1));

        assertThat(service.findRecognitions(offering)).isEmpty();
    }

    private CourseOffering offering(
            int year,
            int semester,
            String code,
            String name,
            String department,
            String major
    ) {
        CourseOffering offering = mock(CourseOffering.class);
        when(offering.getSemester()).thenReturn(Semester.create(year, semester));
        when(offering.getCourseCode()).thenReturn(code);
        when(offering.getCourseName()).thenReturn(name);
        when(offering.getAcademicUnit()).thenReturn(
                AcademicUnit.create("unit", department, department, major)
        );
        return offering;
    }

    private CrossMajorRecognitionRule rule(
            String studentCollege,
            String studentDepartment,
            String studentMajor,
            String offeringDepartment,
            String offeringMajor,
            String code,
            String courseName,
            int effectiveYear,
            int effectiveSemester,
            int sourceRow
    ) {
        String line = String.join("|", studentCollege, studentDepartment, studentMajor,
                offeringDepartment, offeringMajor, code, courseName,
                Integer.toString(effectiveYear), Integer.toString(effectiveSemester));
        CrossMajorRecognitionRuleData data = new CrossMajorRecognitionRuleData(
                hash(line),
                studentCollege,
                studentDepartment,
                studentMajor,
                "개설대학",
                offeringDepartment,
                offeringMajor,
                CrossMajorRecognitionNormalizer.key(offeringDepartment),
                CrossMajorRecognitionNormalizer.key(offeringMajor),
                code,
                courseName,
                CrossMajorRecognitionNormalizer.key(courseName),
                effectiveYear,
                effectiveSemester,
                "rules",
                sourceRow
        );
        return CrossMajorRecognitionRule.create(history, data);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
