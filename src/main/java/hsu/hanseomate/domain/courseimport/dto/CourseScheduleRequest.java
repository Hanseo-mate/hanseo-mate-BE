package hsu.hanseomate.domain.courseimport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CourseScheduleRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotEmpty List<@NotNull Integer> periods,
        @Valid ClassroomRequest classroom
) {
}
