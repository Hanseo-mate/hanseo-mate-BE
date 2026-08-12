package hsu.hanseomate.domain.timetable.composition.service;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.course.repository.OfferingEligibleDepartmentNameProjection;
import hsu.hanseomate.domain.course.repository.OfferingEligibleDepartmentRepository;
import hsu.hanseomate.domain.timetable.composition.currentuser.CurrentUserIdProvider;
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
                toCourseResponses(timetableCourses)
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
                .filter(existing -> conflictDetector.conflicts(
                        candidateSchedules,
                        schedulesByOffering.getOrDefault(
                                existing.getCourseOffering().getId(),
                                List.of()
                        )
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
                    loadEligibleDepartmentNames(List.of(candidate.getId()))
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
        List<UUID> offeringIds = new ArrayList<>(existingCourses.size() + 1);
        offeringIds.add(candidate.getId());
        existingCourses.stream()
                .map(TimetableCourse::getCourseOffering)
                .map(CourseOffering::getId)
                .forEach(offeringIds::add);
        return courseScheduleRepository.findAllForOfferings(offeringIds)
                .stream()
                .collect(Collectors.groupingBy(
                        schedule -> schedule.getOffering().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private List<TimetableCourseResponse> toCourseResponses(
            List<TimetableCourse> timetableCourses
    ) {
        if (timetableCourses.isEmpty()) {
            return List.of();
        }
        List<UUID> offeringIds = timetableCourses.stream()
                .map(TimetableCourse::getCourseOffering)
                .map(CourseOffering::getId)
                .toList();
        Map<UUID, List<CourseSchedule>> schedulesByOffering =
                courseScheduleRepository.findAllForOfferings(offeringIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                schedule -> schedule.getOffering().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));
        return toCourseResponses(timetableCourses, schedulesByOffering);
    }

    private List<TimetableCourseResponse> toCourseResponses(
            List<TimetableCourse> timetableCourses,
            Map<UUID, List<CourseSchedule>> schedulesByOffering
    ) {
        List<UUID> offeringIds = timetableCourses.stream()
                .map(TimetableCourse::getCourseOffering)
                .map(CourseOffering::getId)
                .toList();
        Map<UUID, List<String>> eligibleDepartmentsByOffering =
                loadEligibleDepartmentNames(offeringIds);
        return timetableCourses.stream()
                .map(timetableCourse -> TimetableCourseResponse.from(
                        timetableCourse,
                        schedulesByOffering.getOrDefault(
                                timetableCourse.getCourseOffering().getId(),
                                List.of()
                        ),
                        eligibleDepartmentsByOffering.getOrDefault(
                                timetableCourse.getCourseOffering().getId(),
                                List.of()
                        )
                ))
                .toList();
    }

    private Map<UUID, List<String>> loadEligibleDepartmentNames(List<UUID> offeringIds) {
        if (offeringIds.isEmpty()) {
            return Map.of();
        }
        return offeringEligibleDepartmentRepository.findNamesByOfferingIds(offeringIds)
                .stream()
                .collect(Collectors.groupingBy(
                        OfferingEligibleDepartmentNameProjection::getOfferingId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                OfferingEligibleDepartmentNameProjection::getDepartmentName,
                                Collectors.toList()
                        )
                ));
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
