package hsu.hanseomate.domain.timetable.search.service;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.course.repository.OfferingEligibleDepartmentNameProjection;
import hsu.hanseomate.domain.course.repository.OfferingEligibleDepartmentRepository;
import hsu.hanseomate.domain.course.support.CoursePeriodPolicy;
import hsu.hanseomate.domain.courseenrichment.crossmajor.service.CrossMajorRecognitionQueryService;
import hsu.hanseomate.domain.courseenrichment.equivalence.service.EquivalentCourseQueryService;
import hsu.hanseomate.domain.timetable.search.dto.CourseOfferingDetailResponse;
import hsu.hanseomate.domain.timetable.search.dto.CourseOfferingPageResponse;
import hsu.hanseomate.domain.timetable.search.dto.CourseOfferingResponse;
import hsu.hanseomate.domain.timetable.search.dto.CourseSearchCondition;
import hsu.hanseomate.domain.timetable.search.exception.CourseOfferingNotFoundException;
import hsu.hanseomate.domain.timetable.search.specification.CourseSearchSpecifications;
import hsu.hanseomate.domain.timetable.search.type.CourseCreditFilter;
import hsu.hanseomate.domain.timetable.search.type.CourseGradeFilter;
import hsu.hanseomate.domain.timetable.search.type.CourseSearchField;
import hsu.hanseomate.domain.timetable.search.type.CourseSortOption;
import hsu.hanseomate.domain.timetable.search.type.GeneralCategoryFilter;
import hsu.hanseomate.global.exception.BadRequestException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseSearchService {

    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final OfferingEligibleDepartmentRepository offeringEligibleDepartmentRepository;
    private final EquivalentCourseQueryService equivalentCourseQueryService;
    private final CrossMajorRecognitionQueryService crossMajorRecognitionQueryService;

    public CourseOfferingPageResponse search(
            CourseSearchCondition requestedCondition,
            int page,
            int size
    ) {
        CourseSearchCondition condition = normalize(requestedCondition);
        validateTimeRange(condition.startPeriod(), condition.endPeriod());

        PageRequest pageRequest = PageRequest.of(page, size, sort(condition.sort()));
        Page<CourseOffering> offeringPage = courseOfferingRepository.findAll(
                CourseSearchSpecifications.from(condition),
                pageRequest
        );
        List<CourseOffering> offerings = offeringPage.getContent();

        List<UUID> courseIds = offerings.stream()
                .map(offering -> offering.getCourse().getId())
                .distinct()
                .toList();
        Map<UUID, List<CourseSchedule>> schedulesByCourse = courseIds.isEmpty()
                ? Map.of()
                : courseScheduleRepository.findAllForCourses(courseIds)
                        .stream()
                        .collect(Collectors.groupingBy(schedule -> schedule.getCourse().getId()));
        Map<UUID, List<String>> eligibleDepartmentsByCourse =
                loadEligibleDepartmentNames(courseIds);

        List<CourseOfferingResponse> items = offerings.stream()
                .map(offering -> CourseOfferingResponse.from(
                        offering,
                        schedulesByCourse.getOrDefault(offering.getCourse().getId(), List.of()),
                        eligibleDepartmentsByCourse.getOrDefault(
                                offering.getCourse().getId(),
                                List.of()
                        )
                ))
                .toList();

        return new CourseOfferingPageResponse(
                items,
                offeringPage.getNumber(),
                offeringPage.getSize(),
                offeringPage.getTotalPages(),
                offeringPage.getTotalElements(),
                offeringPage.hasNext()
        );
    }

    public CourseOfferingDetailResponse getCourse(UUID offeringId) {
        CourseOffering offering = courseOfferingRepository.findDetailedById(offeringId)
                .orElseThrow(() -> new CourseOfferingNotFoundException(offeringId));
        UUID courseId = offering.getCourse().getId();
        List<CourseSchedule> schedules = courseScheduleRepository.findAllForCourses(
                List.of(courseId)
        );
        List<String> eligibleDepartmentNames = loadEligibleDepartmentNames(List.of(courseId))
                .getOrDefault(courseId, List.of());
        return CourseOfferingDetailResponse.from(
                offering,
                schedules,
                eligibleDepartmentNames,
                equivalentCourseQueryService.findEquivalentCourses(offering),
                crossMajorRecognitionQueryService.findRecognitions(offering)
        );
    }

    private Map<UUID, List<String>> loadEligibleDepartmentNames(List<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return offeringEligibleDepartmentRepository.findNamesByCourseIds(courseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        OfferingEligibleDepartmentNameProjection::getCourseId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                OfferingEligibleDepartmentNameProjection::getDepartmentName,
                                Collectors.toList()
                        )
                ));
    }

    private CourseSearchCondition normalize(CourseSearchCondition condition) {
        Set<GeneralCategoryFilter> generalCategories =
                normalizeEnumSet(condition.generalCategories(), GeneralCategoryFilter.class);
        Set<CourseGradeFilter> grades =
                normalizeEnumSet(condition.grades(), CourseGradeFilter.class);
        Set<CourseCreditFilter> credits =
                normalizeEnumSet(condition.credits(), CourseCreditFilter.class);

        Integer startPeriod = condition.startPeriod();
        Integer endPeriod = condition.endPeriod();
        if (Integer.valueOf(CoursePeriodPolicy.MIN_PERIOD).equals(startPeriod)
                && Integer.valueOf(CoursePeriodPolicy.MAX_PERIOD).equals(endPeriod)) {
            startPeriod = null;
            endPeriod = null;
        }

        return new CourseSearchCondition(
                condition.academicYear(),
                condition.semester(),
                condition.curriculumType(),
                normalizeAcademicUnits(condition.academicUnits()),
                generalCategories,
                condition.searchField() == null
                        ? CourseSearchField.COURSE_NAME
                        : condition.searchField(),
                normalizeAndEscapeKeyword(condition.keyword()),
                condition.sort() == null ? CourseSortOption.DEFAULT : condition.sort(),
                startPeriod,
                endPeriod,
                grades,
                credits
        );
    }

    private Set<String> normalizeAcademicUnits(Set<String> academicUnits) {
        if (academicUnits == null || academicUnits.isEmpty()) {
            return Set.of();
        }
        return academicUnits.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private <E extends Enum<E>> Set<E> normalizeEnumSet(Set<E> values, Class<E> enumType) {
        if (values == null || values.isEmpty() || values.size() == enumType.getEnumConstants().length) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private String normalizeAndEscapeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private void validateTimeRange(Integer startPeriod, Integer endPeriod) {
        if ((startPeriod == null) != (endPeriod == null)) {
            throw new BadRequestException("시작 교시와 종료 교시는 함께 입력해야 합니다.");
        }
        if (startPeriod == null) {
            return;
        }
        if (startPeriod < CoursePeriodPolicy.MIN_PERIOD
                || endPeriod > CoursePeriodPolicy.MAX_PERIOD) {
            throw new BadRequestException("교시는 0~30 범위여야 합니다.");
        }
        if (startPeriod > endPeriod) {
            throw new BadRequestException("시작 교시는 종료 교시보다 클 수 없습니다.");
        }
    }

    private Sort sort(CourseSortOption sortOption) {
        List<Sort.Order> orders = switch (sortOption) {
            case DEFAULT -> List.of(
                    Sort.Order.desc("semester.academicYear"),
                    Sort.Order.desc("semester.semester"),
                    Sort.Order.asc("sourceSheet"),
                    Sort.Order.asc("sourceRow"),
                    Sort.Order.asc("id")
            );
            case COURSE_CODE -> List.of(
                    Sort.Order.desc("semester.academicYear"),
                    Sort.Order.desc("semester.semester"),
                    Sort.Order.asc("course.courseCode"),
                    Sort.Order.asc("course.sectionNo"),
                    Sort.Order.asc("id")
            );
            case COURSE_NAME -> List.of(
                    Sort.Order.desc("semester.academicYear"),
                    Sort.Order.desc("semester.semester"),
                    Sort.Order.asc("course.courseName"),
                    Sort.Order.asc("course.courseCode"),
                    Sort.Order.asc("course.sectionNo"),
                    Sort.Order.asc("id")
            );
        };
        return Sort.by(orders);
    }

}
