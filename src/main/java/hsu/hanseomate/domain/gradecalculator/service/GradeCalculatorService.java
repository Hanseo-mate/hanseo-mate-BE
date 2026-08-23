package hsu.hanseomate.domain.gradecalculator.service;

import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationCourseRequest;
import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationRequest;
import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationResponse;
import hsu.hanseomate.domain.gradecalculator.dto.GradeCompactSummaryResponse;
import hsu.hanseomate.domain.gradecalculator.dto.GradeCourseUpdateRequest;
import hsu.hanseomate.domain.gradecalculator.dto.GradeOverviewResponse;
import hsu.hanseomate.domain.gradecalculator.dto.GradeSummaryResponse;
import hsu.hanseomate.domain.gradecalculator.dto.GradeTermSummaryResponse;
import hsu.hanseomate.domain.gradecalculator.dto.TimetableGradeCourseResponse;
import hsu.hanseomate.domain.gradecalculator.dto.TimetableGradeCoursesResponse;
import hsu.hanseomate.domain.gradecalculator.type.ExpectedGrade;
import hsu.hanseomate.domain.gradecalculator.type.GradeCalculationStatus;
import hsu.hanseomate.domain.timetable.composition.currentuser.CurrentUserIdProvider;
import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.composition.exception.TimetableApiException;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import hsu.hanseomate.domain.timetable.composition.type.TimetableErrorCode;
import hsu.hanseomate.global.exception.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

        BigDecimal expectedGpa = averageGpa(weightedGradePoints, gpaCredits);
        GradeCalculationStatus status = resolveStatus(
                request.courses().size(),
                ungradedCourseCount,
                0
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

    public GradeOverviewResponse getOverview() {
        Long ownerId = currentUserIdProvider.currentUserId();
        List<Timetable> timetables = timetableRepository
                .findAllByOwnerIdOrderByAcademicYearDescSemesterDesc(ownerId);
        List<TimetableCourse> allCourses = timetableCourseRepository
                .findAllByOwnerIdForGradeCalculation(ownerId);
        Map<Long, List<TimetableCourse>> coursesByTimetable = allCourses.stream()
                .collect(Collectors.groupingBy(
                        course -> course.getTimetable().getId()
                ));

        List<GradeTermSummaryResponse> terms = timetables.stream()
                .map(timetable -> {
                    List<TimetableCourse> termCourses = coursesByTimetable
                            .getOrDefault(timetable.getId(), List.of());
                    return GradeTermSummaryResponse.from(
                            timetable,
                            termCourses.size(),
                            calculateSummary(termCourses)
                    );
                })
                .toList();

        return new GradeOverviewResponse(
                MAXIMUM_GPA,
                calculateSummary(allCourses),
                terms
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
        return buildTimetableResponse(timetable, ownerId);
    }

    public GradeCompactSummaryResponse getCompactSummary(Long timetableId) {
        Long ownerId = currentUserIdProvider.currentUserId();
        Timetable timetable = findOwnedTimetable(timetableId, ownerId);
        List<TimetableCourse> termCourses = timetableCourseRepository
                .findAllByTimetableIdOrderById(timetable.getId());
        List<TimetableCourse> allCourses = timetableCourseRepository
                .findAllByOwnerIdForGradeCalculation(ownerId);
        return new GradeCompactSummaryResponse(
                calculateSummary(termCourses),
                calculateSummary(allCourses)
        );
    }

    @Transactional
    public TimetableGradeCoursesResponse updateExpectedGrade(
            Long timetableCourseId,
            GradeCourseUpdateRequest request
    ) {
        if (request == null || !request.hasExpectedGrade()) {
            throw new BadRequestException("expectedGrade 필드는 필수입니다.");
        }
        Long ownerId = currentUserIdProvider.currentUserId();
        TimetableCourse timetableCourse = timetableCourseRepository
                .findOwnedByIdForUpdate(timetableCourseId, ownerId)
                .orElseThrow(() -> new TimetableApiException(
                        TimetableErrorCode.TIMETABLE_COURSE_NOT_FOUND
                ));
        timetableCourse.updateExpectedGrade(request.expectedGrade());
        return buildTimetableResponse(timetableCourse.getTimetable(), ownerId);
    }

    private TimetableGradeCoursesResponse buildTimetableResponse(
            Timetable timetable,
            Long ownerId
    ) {
        List<TimetableCourse> termCourses = timetableCourseRepository
                .findAllByTimetableIdOrderById(timetable.getId());
        List<TimetableGradeCourseResponse> courses = termCourses.stream()
                .map(TimetableGradeCourseResponse::from)
                .toList();
        List<TimetableCourse> allCourses = timetableCourseRepository
                .findAllByOwnerIdForGradeCalculation(ownerId);
        return TimetableGradeCoursesResponse.from(
                timetable,
                courses,
                calculateSummary(termCourses),
                calculateSummary(allCourses)
        );
    }

    private Timetable findOwnedTimetable(Long timetableId, Long ownerId) {
        return timetableRepository.findByIdAndOwnerId(timetableId, ownerId)
                .orElseThrow(() -> new TimetableApiException(
                        TimetableErrorCode.TIMETABLE_NOT_FOUND
                ));
    }

    private GradeSummaryResponse calculateSummary(
            List<TimetableCourse> courses
    ) {
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal gpaCredits = BigDecimal.ZERO;
        BigDecimal earnedCredits = BigDecimal.ZERO;
        BigDecimal weightedGradePoints = BigDecimal.ZERO;
        int ungradedCourseCount = 0;
        int unavailableCreditCourseCount = 0;

        for (TimetableCourse course : courses) {
            BigDecimal credit = course.getCourseOffering().getCredit();
            ExpectedGrade grade = course.getExpectedGrade();
            if (grade == null) {
                ungradedCourseCount++;
            }
            if (credit == null || credit.signum() <= 0) {
                unavailableCreditCourseCount++;
                continue;
            }

            totalCredits = totalCredits.add(credit);
            if (grade == null) {
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

        return new GradeSummaryResponse(
                MAXIMUM_GPA,
                totalCredits,
                gpaCredits,
                earnedCredits,
                averageGpa(weightedGradePoints, gpaCredits),
                ungradedCourseCount,
                unavailableCreditCourseCount,
                resolveStatus(
                        courses.size(),
                        ungradedCourseCount,
                        unavailableCreditCourseCount
                )
        );
    }

    private BigDecimal averageGpa(
            BigDecimal weightedGradePoints,
            BigDecimal gpaCredits
    ) {
        return gpaCredits.signum() == 0
                ? null
                : weightedGradePoints.divide(gpaCredits, 2, RoundingMode.HALF_UP);
    }

    private GradeCalculationStatus resolveStatus(
            int courseCount,
            int ungradedCourseCount,
            int unavailableCreditCourseCount
    ) {
        if (courseCount == 0) {
            return GradeCalculationStatus.EMPTY;
        }
        if (ungradedCourseCount > 0 || unavailableCreditCourseCount > 0) {
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
