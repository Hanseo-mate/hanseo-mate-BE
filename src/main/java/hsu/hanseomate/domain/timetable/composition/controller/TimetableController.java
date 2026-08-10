package hsu.hanseomate.domain.timetable.composition.controller;

import hsu.hanseomate.domain.timetable.composition.dto.TimetableCourseAddRequest;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableCourseResponse;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableCreateRequest;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableCreateResponse;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableDetailResponse;
import hsu.hanseomate.domain.timetable.composition.dto.TimetableTermResponse;
import hsu.hanseomate.domain.timetable.composition.service.TimetableService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timetables")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;

    @PostMapping
    public ResponseEntity<TimetableCreateResponse> create(
            @RequestBody TimetableCreateRequest request
    ) {
        TimetableCreateResponse response = timetableService.create(request);
        return ResponseEntity
                .created(URI.create(
                        "/api/timetables?year="
                                + response.year()
                                + "&semester="
                                + response.semester()
                ))
                .body(response);
    }

    @GetMapping
    public TimetableDetailResponse getByTerm(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer semester
    ) {
        return timetableService.getByTerm(year, semester);
    }

    @GetMapping("/terms")
    public List<TimetableTermResponse> getTerms() {
        return timetableService.getTerms();
    }

    @PostMapping("/courses/{timetableId}")
    public ResponseEntity<TimetableCourseResponse> addCourse(
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableCourseAddRequest request
    ) {
        return ResponseEntity.status(201)
                .body(timetableService.addCourse(timetableId, request));
    }

    @DeleteMapping("/courses/{timetableCourseId}")
    public ResponseEntity<Void> deleteCourse(
            @PathVariable Long timetableCourseId
    ) {
        timetableService.deleteCourse(timetableCourseId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{timetableId}")
    public ResponseEntity<Void> deleteTimetable(@PathVariable Long timetableId) {
        timetableService.deleteTimetable(timetableId);
        return ResponseEntity.noContent().build();
    }
}
