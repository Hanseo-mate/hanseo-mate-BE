package hsu.hanseomate.domain.academiccalendar.controller;
import hsu.hanseomate.domain.academiccalendar.service.AcademicCalendarSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/academic-calendar")
public class AdminAcademicCalendarController {
    private final AcademicCalendarSyncService service;
    @PostMapping("/sync")
    public AcademicCalendarSyncService.SyncResult sync() { return service.sync(); }
}
