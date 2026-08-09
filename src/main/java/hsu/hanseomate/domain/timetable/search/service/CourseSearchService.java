package hsu.hanseomate.domain.timetable.search.service;

import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.course.support.CoursePeriodPolicy;
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

        List<UUID> offeringIds = offerings.stream().map(CourseOffering::getId).toList();
        Map<UUID, List<CourseSchedule>> schedulesByOffering = offeringIds.isEmpty()
                ? Map.of()
                : courseScheduleRepository.findAllForOfferings(offeringIds)
                        .stream()
                        .collect(Collectors.groupingBy(schedule -> schedule.getOffering().getId()));

        List<CourseOfferingResponse> items = offerings.stream()
                .map(offering -> CourseOfferingResponse.from(
                        offering,
                        schedulesByOffering.getOrDefault(offering.getId(), List.of())
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
        CourseOffering offering = courseOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new CourseOfferingNotFoundException(offeringId));
        List<CourseSchedule> schedules = courseScheduleRepository.findAllForOfferings(
                List.of(offeringId)
        );
        return CourseOfferingDetailResponse.from(offering, schedules);
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
                    Sort.Order.asc("courseCode"),
                    Sort.Order.asc("sectionNo"),
                    Sort.Order.asc("id")
            );
            case COURSE_NAME -> List.of(
                    Sort.Order.desc("semester.academicYear"),
                    Sort.Order.desc("semester.semester"),
                    Sort.Order.asc("courseName"),
                    Sort.Order.asc("courseCode"),
                    Sort.Order.asc("sectionNo"),
                    Sort.Order.asc("id")
            );
        };
        return Sort.by(orders);
    }

}
