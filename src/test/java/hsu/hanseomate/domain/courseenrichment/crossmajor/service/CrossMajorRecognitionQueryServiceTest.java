package hsu.hanseomate.domain.courseenrichment.crossmajor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.Semester;
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
    void matchesNormalizedCourseNameAndEffectivePeriodThenSortsDistinct() {
        CourseOffering offering = offering(2026, 1, "자료 구조");
        List<CrossMajorRecognitionRule> candidates = List.of(
                rule("학생대학", "나학과", "나전공", "다른학과", "다른전공",
                        "9999999", "자료구조", 2020, 1, 2),
                rule("학생대학", "가학과", "가전공", "개설학과", "개설전공",
                        "0004436", "자료구조", 2020, 1, 3),
                rule("학생대학", "가학과", "가전공", "또다른학과", "또다른전공",
                        "1234567", "자료구조", 2020, 1, 4),
                rule("학생대학", "미래학과", "미래전공", "개설학과", "개설전공",
                        "0004436", "자료구조", 2026, 2, 5)
        );
        when(repository.findActiveCandidatesByCourseName(
                2026, "자료구조", CrossMajorRecognitionImportStatus.ACTIVE
        )).thenReturn(candidates);

        List<String> result = service.findRecognitions(offering);

        assertThat(result).containsExactly("가학과", "나학과");
        verify(repository).findActiveCandidatesByCourseName(
                2026, "자료구조", CrossMajorRecognitionImportStatus.ACTIVE
        );
    }

    @Test
    void matchesCourseNameEvenWhenCodeAndOrganizationDiffer() {
        CourseOffering offering = offering(2026, 2, "안드로이드 프로그래밍");
        when(repository.findActiveCandidatesByCourseName(
                2026, "안드로이드프로그래밍", CrossMajorRecognitionImportStatus.ACTIVE
        )).thenReturn(List.of(
                rule("이공학부", "컴퓨터공학과", "컴퓨터공학전공",
                        "항공컴퓨터전공", "항공컴퓨터전공",
                        "0008032", "안드로이드프로그래밍", 2019, 1, 2),
                rule("항공학부", "항공소프트웨어공학과", "항공소프트웨어공학전공",
                        "완전히다른학과", "완전히다른전공",
                        "9999999", "안드로이드프로그래밍", 2023, 2, 3)
        ));

        assertThat(service.findRecognitions(offering)).containsExactly(
                "컴퓨터공학과",
                "항공소프트웨어공학과"
        );
    }

    @Test
    void blankCourseNameReturnsEmptyWithoutRepositoryLookup() {
        CourseOffering offering = offering(2026, 1, "   ");

        assertThat(service.findRecognitions(offering)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void missingSemesterReturnsEmptyWithoutRepositoryLookup() {
        CourseOffering offering = mock(CourseOffering.class);
        when(offering.getCourseName()).thenReturn("자료구조");

        assertThat(service.findRecognitions(offering)).isEmpty();
        verifyNoInteractions(repository);
    }

    private CourseOffering offering(int year, int semester, String name) {
        CourseOffering offering = mock(CourseOffering.class);
        when(offering.getSemester()).thenReturn(Semester.create(year, semester));
        when(offering.getCourseName()).thenReturn(name);
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
