package hsu.hanseomate.domain.courseimport.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record ParseStatisticsRequest(
        @PositiveOrZero int sheetCount,
        @PositiveOrZero int totalRowCount,
        @PositiveOrZero int parsedLectureCount,
        @PositiveOrZero int failedRowCount,
        @PositiveOrZero int warningCount,
        @PositiveOrZero int errorCount,
        @PositiveOrZero int academicUnitCount,
        @PositiveOrZero int generalCategoryCount,
        @PositiveOrZero int generalCategoryNodeCount,
        @PositiveOrZero int scheduleCount,
        @PositiveOrZero int periodCount,
        @PositiveOrZero int missingScheduleCount,
        @PositiveOrZero int missingClassroomCount
) {
}
