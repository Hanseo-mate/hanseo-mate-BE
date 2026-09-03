package hsu.hanseomate.domain.timetable.composition.service;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.course.repository.OfferingEligibleDepartmentNameProjection;
import hsu.hanseomate.domain.course.repository.OfferingEligibleDepartmentRepository;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import hsu.hanseomate.domain.gradecalculator.service.GradeCalculatorService;
import hsu.hanseomate.domain.timetable.composition.currentuser.CurrentUserIdProvider;
import hsu.hanseomate.domain.timetable.composition.dto.CustomTimetableCourseCreateRequest;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableCourseAddRequest;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableCourseResponse;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableCreateRequest;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableCreateResponse;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableDetailResponse;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableTermResponse;
import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.composition.exception.TimetableApiException;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import hsu.hanseomate.domain.timetable.composition.type.ConflictPolicy;
import hsu.hanseomate.domain.timetable.composition.type.TimetableErrorCode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimetableService {

    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final TimetableRepository timetableRepository;
    private final TimetableCourseRepository timetableCourseRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final OfferingEligibleDepartmentRepository offeringEligibleDepartmentRepository;
    private final TimetableConflictDetector conflictDetector;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final GradeCalculatorService gradeCalculatorService;

    @Transactional
    public TimetableCreateResponse create(TimetableCreateRequest request) {
        validateTerm(request.year(), request.semester());
        Long ownerId = currentUserIdProvider.currentUserId();

        if (timetableRepository.existsByOwnerIdAndAcademicYearAndSemester(
                ownerId,
                request.year(),
                request.semester()
        )) {
            throw new TimetableApiException(TimetableErrorCode.TIMETABLE_ALREADY_EXISTS);
        }

        try {
            Timetable timetable = timetableRepository.saveAndFlush(
                    Timetable.create(ownerId, request.year(), request.semester())
            );
            return TimetableCreateResponse.from(timetable);
        } catch (DataIntegrityViolationException exception) {
            if (violatesConstraint(exception, "uk_timetable_owner_term")) {
                throw new TimetableApiException(
                        TimetableErrorCode.TIMETABLE_ALREADY_EXISTS
                );
            }
            throw exception;
        }
    }

    public TimetableDetailResponse getByTerm(Integer year, Integer semester) {
        validateTerm(year, semester);
        Long ownerId = currentUserIdProvider.currentUserId();
        Timetable timetable = timetableRepository
                .findByOwnerIdAndAcademicYearAndSemester(ownerId, year, semester)
                .orElseThrow(() -> new TimetableApiException(
                        TimetableErrorCode.TIMETABLE_NOT_FOUND
                ));

        List<TimetableCourse> timetableCourses =
                timetableCourseRepository.findAllByTimetableIdOrderById(timetable.getId());
        return TimetableDetailResponse.from(
                timetable,
                toCourseResponses(timetableCourses),
                gradeCalculatorService.getCompactSummary(timetable.getId())
        );
    }

    public List<TimetableTermResponse> getTerms() {
        Long ownerId = currentUserIdProvider.currentUserId();
        return timetableRepository
                .findAllByOwnerIdOrderByAcademicYearDescSemesterDesc(ownerId)
                .stream()
                .map(TimetableTermResponse::from)
                .toList();
    }

    @Transactional
    public TimetableCourseResponse addCourse(
            Long timetableId,
            TimetableCourseAddRequest request
    ) {
        Long ownerId = currentUserIdProvider.currentUserId();
        Timetable timetable = findOwnedForUpdate(timetableId, ownerId);
        CourseOffering candidate = courseOfferingRepository.findDetailedById(request.courseId())
                .orElseThrow(() -> new TimetableApiException(
                        TimetableErrorCode.COURSE_NOT_FOUND
                ));

        validateCourseTerm(timetable, candidate);
        ensureNotAlreadyAdded(timetableId, candidate.getId());

        List<TimetableCourse> existingCourses =
                timetableCourseRepository.findAllByTimetableIdOrderById(timetableId);
        Map<UUID, List<CourseSchedule>> schedulesByOffering =
                loadSchedules(existingCourses, candidate);
        List<CourseSchedule> candidateSchedules =
                schedulesByOffering.getOrDefault(candidate.getId(), List.of());

        List<TimetableCourse> conflicts = existingCourses.stream()
                .filter(existing -> conflictsWithRegisteredCourse(
                        candidateSchedules,
                        existing,
                        schedulesByOffering
                ))
                .toList();

        if (!conflicts.isEmpty()
                && request.effectiveConflictPolicy() == ConflictPolicy.REJECT) {
            throw new TimetableApiException(
                    TimetableErrorCode.TIMETABLE_TIME_CONFLICT,
                    toCourseResponses(conflicts, schedulesByOffering)
            );
        }

        if (!conflicts.isEmpty()) {
            timetableCourseRepository.deleteAllInBatch(conflicts);
        }

        try {
            TimetableCourse created = timetableCourseRepository.saveAndFlush(
                    TimetableCourse.create(timetable, candidate)
            );
            return TimetableCourseResponse.from(
                    created,
                    candidateSchedules,
                    loadEligibleDepartmentNames(List.of(candidate))
                            .getOrDefault(candidate.getId(), List.of())
            );
        } catch (DataIntegrityViolationException exception) {
            if (violatesConstraint(exception, "uk_timetable_course_offering")) {
                throw new TimetableApiException(TimetableErrorCode.COURSE_ALREADY_ADDED);
            }
            throw exception;
        }
    }

    @Transactional
    public TimetableCourseResponse addCustomCourse(
            Long timetableId,
            CustomTimetableCourseCreateRequest request
    ) {
        validateCustomTimeRange(request.startTime(), request.endTime());
        Long ownerId = currentUserIdProvider.currentUserId();
        Timetable timetable = findOwnedForUpdate(timetableId, ownerId);
        List<TimetableCourse> existingCourses =
                timetableCourseRepository.findAllByTimetableIdOrderById(timetableId);
        Map<UUID, List<CourseSchedule>> schedulesByOffering =
                loadSchedulesForOfferings(existingCourses.stream()
                        .filter(course -> !course.isCustomCourse())
                        .map(TimetableCourse::getCourseOffering)
                        .toList());

        List<TimetableCourse> conflicts = existingCourses.stream()
                .filter(existing -> conflictsWithCustomCourse(
                        request.dayOfWeek(),
                        request.startTime(),
                        request.endTime(),
                        existing,
                        schedulesByOffering
                ))
                .toList();
        if (!conflicts.isEmpty()) {
            throw new TimetableApiException(
                    TimetableErrorCode.TIMETABLE_TIME_CONFLICT,
                    toCourseResponses(conflicts, schedulesByOffering)
            );
        }

        TimetableCourse created = timetableCourseRepository.saveAndFlush(
                TimetableCourse.createCustom(
                        timetable,
                        request.courseName().strip(),
                        request.credit(),
                        request.dayOfWeek(),
                        request.startTime(),
                        request.endTime()
                )
        );
        return TimetableCourseResponse.fromCustom(created);
    }

    @Transactional
    public void deleteCourse(Long timetableCourseId) {
        Long ownerId = currentUserIdProvider.currentUserId();
        Long timetableId = timetableCourseRepository
                .findTimetableIdById(timetableCourseId)
                .orElseThrow(() -> new TimetableApiException(
                        TimetableErrorCode.TIMETABLE_COURSE_NOT_FOUND
                ));
        Timetable timetable = timetableRepository.findByIdForUpdate(timetableId)
                .orElseThrow(() -> new TimetableApiException(
                        TimetableErrorCode.TIMETABLE_COURSE_NOT_FOUND
                ));
        if (!timetable.isOwnedBy(ownerId)) {
            throw new TimetableApiException(TimetableErrorCode.TIMETABLE_ACCESS_DENIED);
        }
        if (timetableCourseRepository.deleteByIdAndTimetableId(
                timetableCourseId,
                timetableId
        ) == 0) {
            throw new TimetableApiException(
                    TimetableErrorCode.TIMETABLE_COURSE_NOT_FOUND
            );
        }
    }

    @Transactional
    public void deleteTimetable(Long timetableId) {
        Long ownerId = currentUserIdProvider.currentUserId();
        Timetable timetable = findOwnedForUpdate(timetableId, ownerId);
        timetableCourseRepository.deleteAllByTimetableId(timetableId);
        timetableCourseRepository.flush();
        timetableRepository.delete(timetable);
        timetableRepository.flush();
    }

    private Timetable findOwnedForUpdate(Long timetableId, Long ownerId) {
        Timetable timetable = timetableRepository.findByIdForUpdate(timetableId)
                .orElseThrow(() -> new TimetableApiException(
                        TimetableErrorCode.TIMETABLE_NOT_FOUND
                ));
        if (!timetable.isOwnedBy(ownerId)) {
            throw new TimetableApiException(TimetableErrorCode.TIMETABLE_ACCESS_DENIED);
        }
        return timetable;
    }

    private void ensureNotAlreadyAdded(Long timetableId, UUID offeringId) {
        if (timetableCourseRepository.existsByTimetableIdAndCourseOfferingId(
                timetableId,
                offeringId
        )) {
            throw new TimetableApiException(TimetableErrorCode.COURSE_ALREADY_ADDED);
        }
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

    private void validateCourseTerm(Timetable timetable, CourseOffering offering) {
        if (timetable.getAcademicYear() != offering.getSemester().getAcademicYear()
                || timetable.getSemester() != offering.getSemester().getSemester()) {
            throw new TimetableApiException(TimetableErrorCode.COURSE_TERM_MISMATCH);
        }
    }

    private Map<UUID, List<CourseSchedule>> loadSchedules(
            List<TimetableCourse> existingCourses,
            CourseOffering candidate
    ) {
        List<CourseOffering> offerings = new ArrayList<>(existingCourses.size() + 1);
        offerings.add(candidate);
        existingCourses.stream()
                .filter(course -> !course.isCustomCourse())
                .map(TimetableCourse::getCourseOffering)
                .forEach(offerings::add);
        return loadSchedulesForOfferings(offerings);
    }

    private List<TimetableCourseResponse> toCourseResponses(
            List<TimetableCourse> timetableCourses
    ) {
        if (timetableCourses.isEmpty()) {
            return List.of();
        }
        List<CourseOffering> offerings = timetableCourses.stream()
                .filter(course -> !course.isCustomCourse())
                .map(TimetableCourse::getCourseOffering)
                .toList();
        Map<UUID, List<CourseSchedule>> schedulesByOffering =
                loadSchedulesForOfferings(offerings);
        return toCourseResponses(timetableCourses, schedulesByOffering);
    }

    private List<TimetableCourseResponse> toCourseResponses(
            List<TimetableCourse> timetableCourses,
            Map<UUID, List<CourseSchedule>> schedulesByOffering
    ) {
        List<CourseOffering> offerings = timetableCourses.stream()
                .filter(course -> !course.isCustomCourse())
                .map(TimetableCourse::getCourseOffering)
                .toList();
        Map<UUID, List<String>> eligibleDepartmentsByOffering =
                loadEligibleDepartmentNames(offerings);
        return timetableCourses.stream()
                .map(timetableCourse -> {
                    if (timetableCourse.isCustomCourse()) {
                        return TimetableCourseResponse.fromCustom(timetableCourse);
                    }
                    UUID offeringId = timetableCourse.getCourseOffering().getId();
                    return TimetableCourseResponse.from(
                            timetableCourse,
                            schedulesByOffering.getOrDefault(offeringId, List.of()),
                            eligibleDepartmentsByOffering.getOrDefault(
                                    offeringId,
                                    List.of()
                            )
                    );
                })
                .toList();
    }

    private boolean conflictsWithRegisteredCourse(
            List<CourseSchedule> candidateSchedules,
            TimetableCourse existing,
            Map<UUID, List<CourseSchedule>> schedulesByOffering
    ) {
        if (existing.isCustomCourse()) {
            return conflictDetector.conflicts(
                    existing.getCustomDayOfWeek(),
                    existing.getCustomStartTime(),
                    existing.getCustomEndTime(),
                    candidateSchedules
            );
        }
        return conflictDetector.conflicts(
                candidateSchedules,
                schedulesByOffering.getOrDefault(
                        existing.getCourseOffering().getId(),
                        List.of()
                )
        );
    }

    private boolean conflictsWithCustomCourse(
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            TimetableCourse existing,
            Map<UUID, List<CourseSchedule>> schedulesByOffering
    ) {
        if (existing.isCustomCourse()) {
            return conflictDetector.conflicts(
                    dayOfWeek,
                    startTime,
                    endTime,
                    existing.getCustomDayOfWeek(),
                    existing.getCustomStartTime(),
                    existing.getCustomEndTime()
            );
        }
        return conflictDetector.conflicts(
                dayOfWeek,
                startTime,
                endTime,
                schedulesByOffering.getOrDefault(
                        existing.getCourseOffering().getId(),
                        List.of()
                )
        );
    }

    private void validateCustomTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new TimetableApiException(
                    TimetableErrorCode.INVALID_CUSTOM_COURSE_TIME_RANGE
            );
        }
    }

    private Map<UUID, List<CourseSchedule>> loadSchedulesForOfferings(
            List<CourseOffering> offerings
    ) {
        if (offerings.isEmpty()) {
            return Map.of();
        }
        List<UUID> courseIds = offerings.stream()
                .map(offering -> offering.getCourse().getId())
                .distinct()
                .toList();
        Map<UUID, List<CourseSchedule>> schedulesByCourse = courseScheduleRepository
                .findAllForCourses(courseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        schedule -> schedule.getCourse().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<UUID, List<CourseSchedule>> schedulesByOffering = new LinkedHashMap<>();
        offerings.forEach(offering -> schedulesByOffering.put(
                offering.getId(),
                schedulesByCourse.getOrDefault(offering.getCourse().getId(), List.of())
        ));
        return schedulesByOffering;
    }

    private Map<UUID, List<String>> loadEligibleDepartmentNames(
            List<CourseOffering> offerings
    ) {
        if (offerings.isEmpty()) {
            return Map.of();
        }
        List<UUID> courseIds = offerings.stream()
                .map(offering -> offering.getCourse().getId())
                .distinct()
                .toList();
        Map<UUID, List<String>> namesByCourse = offeringEligibleDepartmentRepository
                .findNamesByCourseIds(courseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        OfferingEligibleDepartmentNameProjection::getCourseId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                OfferingEligibleDepartmentNameProjection::getDepartmentName,
                                Collectors.toList()
                        )
                ));
        Map<UUID, List<String>> namesByOffering = new LinkedHashMap<>();
        offerings.forEach(offering -> namesByOffering.put(
                offering.getId(),
                namesByCourse.getOrDefault(offering.getCourse().getId(), List.of())
        ));
        return namesByOffering;
    }

    private boolean violatesConstraint(
            DataIntegrityViolationException exception,
            String constraintName
    ) {
        String normalizedName = constraintName.toLowerCase(Locale.ROOT);
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().equalsIgnoreCase(constraintName)) {
                return true;
            }
            if (cause.getMessage() != null
                    && cause.getMessage().toLowerCase(Locale.ROOT).contains(normalizedName)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
