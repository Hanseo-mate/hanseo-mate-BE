package hsu.hanseomate.domain.gradecalculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationCourseRequest;
import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationRequest;
import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationResponse;
import hsu.hanseomate.domain.gradecalculator.type.ExpectedGrade;
import hsu.hanseomate.domain.gradecalculator.type.GradeCalculationStatus;
import hsu.hanseomate.domain.timetable.composition.currentuser.CurrentUserIdProvider;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GradeCalculatorServiceTest {

    private TimetableRepository timetableRepository;
    private TimetableCourseRepository timetableCourseRepository;
    private CurrentUserIdProvider currentUserIdProvider;
    private GradeCalculatorService service;

    @BeforeEach
    void setUp() {
        timetableRepository = mock(TimetableRepository.class);
        timetableCourseRepository = mock(TimetableCourseRepository.class);
        currentUserIdProvider = mock(CurrentUserIdProvider.class);
        service = new GradeCalculatorService(
                timetableRepository,
                timetableCourseRepository,
                currentUserIdProvider
        );
    }

    @Test
    void calculatesOfficialExampleAndDoesNotRequireTimetableOrLogin() {
        GradeCalculationResponse response = calculate(
                course("자료구조", "3", ExpectedGrade.A_PLUS),
                course("모바일프로그래밍", "2", ExpectedGrade.B),
                course("봉사활동", "1", ExpectedGrade.P)
        );

        assertThat(response.maximumGpa()).isEqualByComparingTo("4.5");
        assertThat(response.appliedCredits()).isEqualByComparingTo("6");
        assertThat(response.gpaCredits()).isEqualByComparingTo("5");
        assertThat(response.earnedCredits()).isEqualByComparingTo("6");
        assertThat(response.expectedGpa().toPlainString()).isEqualTo("3.90");
        assertThat(response.ungradedCourseCount()).isZero();
        assertThat(response.status()).isEqualTo(GradeCalculationStatus.COMPLETE);
        verifyNoInteractions(
                timetableRepository,
                timetableCourseRepository,
                currentUserIdProvider
        );
    }

    @Test
    void includesFInGpaAsZeroButDoesNotEarnCredit() {
        GradeCalculationResponse response = calculate(
                course("재수강 과목", "3", ExpectedGrade.F)
        );

        assertThat(response.appliedCredits()).isEqualByComparingTo("3");
        assertThat(response.gpaCredits()).isEqualByComparingTo("3");
        assertThat(response.earnedCredits()).isZero();
        assertThat(response.expectedGpa().toPlainString()).isEqualTo("0.00");
        assertThat(response.status()).isEqualTo(GradeCalculationStatus.COMPLETE);
    }

    @Test
    void excludesPFromGpaButCountsItAsEarnedCredit() {
        GradeCalculationResponse response = calculate(
                course("봉사활동", "1", ExpectedGrade.P)
        );

        assertThat(response.appliedCredits()).isEqualByComparingTo("1");
        assertThat(response.gpaCredits()).isZero();
        assertThat(response.earnedCredits()).isEqualByComparingTo("1");
        assertThat(response.expectedGpa()).isNull();
        assertThat(response.ungradedCourseCount()).isZero();
        assertThat(response.status()).isEqualTo(GradeCalculationStatus.COMPLETE);
    }

    @Test
    void marksNullGradeAsIncomplete() {
        GradeCalculationResponse response = calculate(
                course("미선택 과목", "3", null)
        );

        assertThat(response.appliedCredits()).isEqualByComparingTo("3");
        assertThat(response.gpaCredits()).isZero();
        assertThat(response.earnedCredits()).isZero();
        assertThat(response.expectedGpa()).isNull();
        assertThat(response.ungradedCourseCount()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(GradeCalculationStatus.INCOMPLETE);
    }

    @Test
    void returnsEmptyStatusForNoCourses() {
        GradeCalculationResponse response = calculate();

        assertThat(response.appliedCredits()).isZero();
        assertThat(response.gpaCredits()).isZero();
        assertThat(response.earnedCredits()).isZero();
        assertThat(response.expectedGpa()).isNull();
        assertThat(response.ungradedCourseCount()).isZero();
        assertThat(response.status()).isEqualTo(GradeCalculationStatus.EMPTY);
    }

    @ParameterizedTest
    @CsvSource({
            "A+, 4.50",
            "A, 4.00",
            "B+, 3.50",
            "B, 3.00",
            "C+, 2.50",
            "C, 2.00",
            "D+, 1.50",
            "D, 1.00",
            "F, 0.00"
    })
    void convertsEveryGpaGrade(String gradeCode, String expectedGpa) {
        GradeCalculationResponse response = calculate(
                course(gradeCode + " 과목", "1", ExpectedGrade.fromCode(gradeCode))
        );

        assertThat(response.expectedGpa().toPlainString()).isEqualTo(expectedGpa);
    }

    @Test
    void roundsExpectedGpaHalfUpToTwoDecimalPlaces() {
        GradeCalculationResponse response = calculate(
                course("A+ 과목", "0.067", ExpectedGrade.A_PLUS),
                course("B 과목", "0.233", ExpectedGrade.B)
        );

        assertThat(response.gpaCredits()).isEqualByComparingTo("0.300");
        assertThat(response.expectedGpa().toPlainString()).isEqualTo("3.34");
    }

    private GradeCalculationResponse calculate(
            GradeCalculationCourseRequest... courses
    ) {
        return service.calculate(new GradeCalculationRequest(List.of(courses)));
    }

    private GradeCalculationCourseRequest course(
            String courseName,
            String credit,
            ExpectedGrade expectedGrade
    ) {
        return new GradeCalculationCourseRequest(
                courseName,
                new BigDecimal(credit),
                expectedGrade,
                null
        );
    }
}
