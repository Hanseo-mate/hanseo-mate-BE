package hsu.hanseomate.domain.gradecalculator.service;

import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationCourseRequest;
import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationRequest;
import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationResponse;
import hsu.hanseomate.domain.gradecalculator.dto.TimetableGradeCourseResponse;
import hsu.hanseomate.domain.gradecalculator.dto.TimetableGradeCoursesResponse;
import hsu.hanseomate.domain.gradecalculator.type.ExpectedGrade;
import hsu.hanseomate.domain.gradecalculator.type.GradeCalculationStatus;
import hsu.hanseomate.domain.timetable.composition.currentuser.CurrentUserIdProvider;
import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import hsu.hanseomate.domain.timetable.composition.exception.TimetableApiException;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import hsu.hanseomate.domain.timetable.composition.type.TimetableErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradeCalculatorService {

    private static final BigDecimal MAXIMUM_GPA = new BigDecimal("4.5");
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final TimetableRepository timetableRepository;
    private final TimetableCourseRepository timetableCourseRepository;
    private final CurrentUserIdProvider currentUserIdProvider;

    public GradeCalculationResponse calculate(GradeCalculationRequest request) {
        BigDecimal appliedCredits = BigDecimal.ZERO;
        BigDecimal gpaCredits = BigDecimal.ZERO;
        BigDecimal earnedCredits = BigDecimal.ZERO;
        BigDecimal weightedGradePoints = BigDecimal.ZERO;
        int ungradedCourseCount = 0;

        for (GradeCalculationCourseRequest course : request.courses()) {
            BigDecimal credit = course.credit();
            ExpectedGrade grade = course.expectedGrade();
            appliedCredits = appliedCredits.add(credit);

            if (grade == null) {
                ungradedCourseCount++;
                continue;
            }
            if (grade.isIncludedInGpa()) {
                gpaCredits = gpaCredits.add(credit);
                weightedGradePoints = weightedGradePoints.add(
                        credit.multiply(grade.getGradePoint())
                );
            }
            if (grade.isCreditEarned()) {
                earnedCredits = earnedCredits.add(credit);
            }
        }

        BigDecimal expectedGpa = gpaCredits.signum() == 0
                ? null
                : weightedGradePoints.divide(gpaCredits, 2, RoundingMode.HALF_UP);
        GradeCalculationStatus status = resolveStatus(
                request.courses(),
                ungradedCourseCount
        );

        return new GradeCalculationResponse(
                MAXIMUM_GPA,
                appliedCredits,
                gpaCredits,
                earnedCredits,
                expectedGpa,
                ungradedCourseCount,
                status
        );
    }

    public TimetableGradeCoursesResponse getTimetableCourses(
            Integer year,
            Integer semester
    ) {
        validateTerm(year, semester);
        Long ownerId = currentUserIdProvider.currentUserId();
        Timetable timetable = timetableRepository
                .findByOwnerIdAndAcademicYearAndSemester(ownerId, year, semester)
                .orElseThrow(() -> new TimetableApiException(
                        TimetableErrorCode.TIMETABLE_NOT_FOUND
                ));
        List<TimetableGradeCourseResponse> courses = timetableCourseRepository
                .findAllByTimetableIdOrderById(timetable.getId())
                .stream()
                .map(TimetableGradeCourseResponse::from)
                .toList();
        return TimetableGradeCoursesResponse.from(timetable, courses);
    }

    private GradeCalculationStatus resolveStatus(
            List<GradeCalculationCourseRequest> courses,
            int ungradedCourseCount
    ) {
        if (courses.isEmpty()) {
            return GradeCalculationStatus.EMPTY;
        }
        if (ungradedCourseCount > 0) {
            return GradeCalculationStatus.INCOMPLETE;
        }
        return GradeCalculationStatus.COMPLETE;
    }

    private void validateTerm(Integer year, Integer semester) {
        if (year == null
                || year < MIN_YEAR
                || year > MAX_YEAR
                || semester == null
                || (semester != 1 && semester != 2)) {
            throw new TimetableApiException(TimetableErrorCode.INVALID_TIMETABLE_TERM);
        }
    }
}
