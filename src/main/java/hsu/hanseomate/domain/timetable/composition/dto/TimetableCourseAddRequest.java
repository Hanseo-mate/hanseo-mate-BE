package hsu.hanseomate.domain.timetable.composition.dto;

import hsu.hanseomate.domain.timetable.composition.type.ConflictPolicy;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TimetableCourseAddRequest(
        @NotNull(message = "과목 ID는 필수입니다.")
        UUID courseId,
        ConflictPolicy conflictPolicy
) {
    public ConflictPolicy effectiveConflictPolicy() {
        return conflictPolicy == null ? ConflictPolicy.REJECT : conflictPolicy;
    }
}
