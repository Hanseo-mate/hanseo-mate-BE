package hsu.hanseomate.domain.course.controller;

import hsu.hanseomate.domain.course.dto.CourseOfferingResponse;
import hsu.hanseomate.domain.course.service.CourseQueryService;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@Tag(name = "강좌 조회", description = "시간표 작성을 위한 학기별 강좌 검색 API")
@Validated
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseQueryService courseQueryService;

    @Operation(summary = "강좌 목록 검색")
    @GetMapping
    public List<CourseOfferingResponse> searchCourses(
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer academicYear,
            @RequestParam(required = false) @Min(1) @Max(2) Integer semester,
            @RequestParam(required = false) CurriculumType curriculumType,
            @RequestParam(required = false) String academicUnit,
            @RequestParam(required = false) GeneralClassification classification,
            @RequestParam(required = false) GeneralArea area,
            @RequestParam(required = false) DeliveryProvider deliveryProvider,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String instructorName
    ) {
        return courseQueryService.search(
                academicYear,
                semester,
                curriculumType,
                academicUnit,
                classification,
                area,
                deliveryProvider,
                courseName,
                instructorName
        );
    }
}
