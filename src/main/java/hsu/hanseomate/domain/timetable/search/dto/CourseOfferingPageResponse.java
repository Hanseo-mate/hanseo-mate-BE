package hsu.hanseomate.domain.timetable.search.dto;

import java.util.List;

public record CourseOfferingPageResponse(
        List<CourseOfferingResponse> items,
        int page,
        int size,
        int totalPages,
        long totalElements,
        boolean hasNext
) {
}
