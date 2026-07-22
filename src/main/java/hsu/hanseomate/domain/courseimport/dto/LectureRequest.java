package hsu.hanseomate.domain.courseimport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.annotation.JsonDeserialize;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record LectureRequest(
        @NotBlank @Size(max = 255) String sourceSheet,
        @Min(value = 1, message = "sourceRow는 1 이상이어야 합니다.") int sourceRow,
        @NotNull CurriculumType curriculumType,
        @Valid AcademicUnitRequest academicUnit,
        @Valid GeneralEducationContextRequest generalEducation,
        @JsonDeserialize(using = StrictNullableStringDeserializer.class)
        @Size(max = 100) String courseCode,
        @Size(max = 255) String courseName,
        @JsonDeserialize(using = StrictNullableStringDeserializer.class)
        @Size(max = 100) String sectionNo,
        Double credit,
        Double classHours,
        @Size(max = 255) String instructorName,
        @Min(value = 1, message = "targetGrade는 1 이상이어야 합니다.")
        @Max(value = 6, message = "targetGrade는 6 이하여야 합니다.")
        Integer targetGrade,
        boolean commonGrade,
        @NotNull List<@NotNull Integer> allowedGrades,
        @NotNull List<@NotNull @Size(max = 255) String> eligibleDepartmentNames,
        Boolean teamTeaching,
        @Size(max = 2000) String note,
        @Size(max = 2000) String eligibilityNote,
        @Size(max = 2000) String scheduleText,
        @Size(max = 2000) String classroomText,
        @NotNull List<@NotNull @Valid CourseScheduleRequest> schedules,
        @NotNull List<@NotNull @Valid SourceCellRequest> sourceCells
) {
}
