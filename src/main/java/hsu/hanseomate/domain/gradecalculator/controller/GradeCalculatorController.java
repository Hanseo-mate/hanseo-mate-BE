package hsu.hanseomate.domain.gradecalculator.controller;

import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationRequest;
import hsu.hanseomate.domain.gradecalculator.dto.GradeCalculationResponse;
import hsu.hanseomate.domain.gradecalculator.dto.GradeCourseUpdateRequest;
import hsu.hanseomate.domain.gradecalculator.dto.GradeOverviewResponse;
import hsu.hanseomate.domain.gradecalculator.dto.GradeScaleResponse;
import hsu.hanseomate.domain.gradecalculator.dto.TimetableGradeCoursesResponse;
import hsu.hanseomate.domain.gradecalculator.service.GradeCalculatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grade-calculations")
@RequiredArgsConstructor
public class GradeCalculatorController {

    private final GradeCalculatorService gradeCalculatorService;

    @GetMapping("/grades")
    public GradeScaleResponse getGradeScale() {
        return GradeScaleResponse.hanseoUniversity();
    }

    @GetMapping("/overview")
    public GradeOverviewResponse getOverview() {
        return gradeCalculatorService.getOverview();
    }

    @PostMapping
    public GradeCalculationResponse calculate(
            @Valid @RequestBody GradeCalculationRequest request
    ) {
        return gradeCalculatorService.calculate(request);
    }

    @GetMapping("/timetable-courses")
    public TimetableGradeCoursesResponse getTimetableCourses(
            @RequestParam Integer year,
            @RequestParam Integer semester
    ) {
        return gradeCalculatorService.getTimetableCourses(year, semester);
    }

    @PatchMapping("/timetable-courses/{timetableCourseId}")
    public TimetableGradeCoursesResponse updateExpectedGrade(
            @PathVariable Long timetableCourseId,
            @RequestBody GradeCourseUpdateRequest request
    ) {
        return gradeCalculatorService.updateExpectedGrade(
                timetableCourseId,
                request
        );
    }
}
