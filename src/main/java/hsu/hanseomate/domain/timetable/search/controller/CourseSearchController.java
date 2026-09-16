package hsu.hanseomate.domain.timetable.search.controller;

import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.timetable.search.dto.CourseOfferingDetailResponse;
import hsu.hanseomate.domain.timetable.search.dto.CourseOfferingPageResponse;
import hsu.hanseomate.domain.timetable.search.dto.CourseSearchCondition;
import hsu.hanseomate.domain.timetable.search.service.CourseSearchService;
import hsu.hanseomate.domain.timetable.search.type.CourseCreditFilter;
import hsu.hanseomate.domain.timetable.search.type.CourseGradeFilter;
import hsu.hanseomate.domain.timetable.search.type.CourseSearchField;
import hsu.hanseomate.domain.timetable.search.type.CourseSortOption;
import hsu.hanseomate.domain.timetable.search.type.GeneralCategoryFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "강좌 조회", description = "에브리타임 형태의 사용자용 강좌 검색 API")
@Validated
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseSearchController {

    private final CourseSearchService courseSearchService;

    @Operation(summary = "강좌 검색")
    @GetMapping
    public CourseOfferingPageResponse searchCourses(
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer academicYear,
            @RequestParam(required = false) @Min(1) @Max(2) Integer semester,
            @RequestParam(required = false) CurriculumType curriculumType,
            @RequestParam(required = false) Set<String> academicUnits,
            @RequestParam(required = false) Set<GeneralCategoryFilter> generalCategories,
            @RequestParam(defaultValue = "COURSE_NAME") CourseSearchField searchField,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "DEFAULT") CourseSortOption sort,
            @RequestParam(required = false) @Min(0) @Max(30) Integer startPeriod,
            @RequestParam(required = false) @Min(0) @Max(30) Integer endPeriod,
            @RequestParam(required = false) Set<CourseGradeFilter> grades,
            @RequestParam(required = false) Set<CourseCreditFilter> credits,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        CourseSearchCondition condition = new CourseSearchCondition(
                academicYear,
                semester,
                curriculumType,
                academicUnits,
                generalCategories,
                searchField,
                keyword,
                sort,
                startPeriod,
                endPeriod,
                grades,
                credits
        );
        return courseSearchService.search(condition, page, size);
    }

    @Operation(summary = "강좌 상세 조회")
    @GetMapping("/{offeringId}")
    public CourseOfferingDetailResponse getCourse(
            @PathVariable UUID offeringId
    ) {
        return courseSearchService.getCourse(offeringId);
    }
}
