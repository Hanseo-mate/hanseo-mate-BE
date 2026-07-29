package hsu.hanseomate.domain.timetable.search.dto;

import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.timetable.search.type.CourseCreditFilter;
import hsu.hanseomate.domain.timetable.search.type.CourseGradeFilter;
import hsu.hanseomate.domain.timetable.search.type.CourseSearchField;
import hsu.hanseomate.domain.timetable.search.type.CourseSortOption;
import hsu.hanseomate.domain.timetable.search.type.GeneralCategoryFilter;
import java.util.Set;

public record CourseSearchCondition(
        Integer academicYear,
        Integer semester,
        CurriculumType curriculumType,
        Set<String> academicUnits,
        Set<GeneralCategoryFilter> generalCategories,
        CourseSearchField searchField,
        String keyword,
        CourseSortOption sort,
        Integer startPeriod,
        Integer endPeriod,
        Set<CourseGradeFilter> grades,
        Set<CourseCreditFilter> credits
) {
}
