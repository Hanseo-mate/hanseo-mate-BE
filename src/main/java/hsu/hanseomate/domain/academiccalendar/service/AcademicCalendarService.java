package hsu.hanseomate.domain.academiccalendar.service;

import hsu.hanseomate.domain.academiccalendar.dto.*;
import hsu.hanseomate.domain.academiccalendar.type.AcademicEventType;
import hsu.hanseomate.domain.schoolcalendar.entity.SchoolCalendarEvent;
import hsu.hanseomate.domain.schoolcalendar.repository.SchoolCalendarEventRepository;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicCalendarService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern LEGACY_EXAM_TITLE =
            Pattern.compile("(?:(\\d{4})학년도)?(?:([12])학기)?(중간고사|기말고사)");
    private final KoreanHolidayCalendar holidays;
    private final SchoolCalendarEventRepository events;

    // Independent of API credentials, network access, and persisted snapshots.
    // Official SEMESTER_START events never override this calculation.
    public Optional<LocalDate> opening(int year, int semester) {
        YearMonth month = YearMonth.of(year, semester == 1 ? 3 : 9);
        return holidays.dates(month).flatMap(closed ->
                month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
                    .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY
                            && d.getDayOfWeek() != DayOfWeek.SUNDAY && !closed.contains(d))
                    .findFirst());
    }

    public Optional<Term> activeTerm(LocalDate date) {
        Term term = latestTerm(date);
        return term != null && term.vacation() != null && date.isBefore(term.vacation())
                ? Optional.of(term) : Optional.empty();
    }

    public boolean isTeachingDate(LocalDate date) { return activeTerm(date).isPresent(); }

    public AcademicOverview overview(LocalDate today) {
        Term term = latestTerm(today);
        if (term == null) return new AcademicOverview("UNKNOWN", null, null);
        // Opening day remains D-DAY even if the vacation date has not been published.
        if (today.equals(term.start())) {
            return new AcademicOverview(term.vacation() == null ? "UNKNOWN" : "IN_SEMESTER",
                    dday("SEMESTER_START", term.start(), term.start(), today),
                    exam(term, today));
        }
        if (term.vacation() == null) return new AcademicOverview("UNKNOWN", null, exam(term, today));
        if (today.isBefore(term.vacation())) {
            return new AcademicOverview("IN_SEMESTER",
                    dday("SEMESTER_END", term.vacation(), term.vacation(), today), exam(term, today));
        }
        if (today.equals(term.vacation())) {
            return new AcademicOverview("VACATION",
                    dday("SEMESTER_END", term.vacation(), term.vacation(), today), null);
        }
        int nextSemester = term.semester() == 1 ? 2 : 1;
        int nextYear = term.year() + (nextSemester == 1 ? 1 : 0);
        AcademicDday next = opening(nextYear, nextSemester)
                .filter(d -> d.isAfter(today))
                .map(d -> dday("SEMESTER_START", d, d, today)).orElse(null);
        return new AcademicOverview("VACATION", next, null);
    }

    private Term latestTerm(LocalDate date) {
        // Nominal March/September boundaries must not select an old term if
        // holiday coverage for the new term is missing.
        int semester = date.getMonthValue() >= 9 ? 2 : date.getMonthValue() >= 3 ? 1 : 2;
        int year = date.getYear() - (date.getMonthValue() < 3 ? 1 : 0);
        Optional<LocalDate> start = opening(year, semester);
        if (start.isEmpty()) return null;
        if (date.isBefore(start.get())) {
            year -= semester == 1 ? 1 : 0;
            semester = semester == 1 ? 2 : 1;
            start = opening(year, semester);
            if (start.isEmpty()) return null;
        }
        List<SchoolCalendarEvent> source = events
                .findBySourceAndAcademicYearAndSemesterAndActiveTrueOrderByStartDateAsc("HANSEO", year, semester);
        final int selectedSemester = semester;
        final int selectedYear = year;
        LocalDate endLimit = LocalDate.of(year + (semester == 2 ? 1 : 0), semester == 1 ? 9 : 3, 1);
        LocalDate opening = start.get();
        // Pre-migration school events have MANUAL/null/null/OTHER metadata.
        // Use them only until this term has a collected snapshot; never combine sources.
        if (source.isEmpty()) {
            source = events.findLegacyAcademicCandidates(opening, endLimit);
        }
        List<SchoolCalendarEvent> vacations = source.stream()
                .filter(e -> effectiveType(e, selectedYear, selectedSemester) == AcademicEventType.VACATION_START)
                .filter(e -> e.getStartDate().isAfter(opening) && e.getStartDate().isBefore(endLimit)).toList();
        LocalDate vacation = vacations.size() == 1 ? vacations.get(0).getStartDate() : null;
        return new Term(selectedYear, selectedSemester, opening, vacation, source);
    }

    private AcademicDday exam(Term term, LocalDate today) {
        Optional<SchoolCalendarEvent> mid = uniqueExam(term, AcademicEventType.MIDTERM);
        Optional<SchoolCalendarEvent> fin = uniqueExam(term, AcademicEventType.FINAL);
        // Do not skip an unpublished midterm and show finals prematurely.
        if (mid.isEmpty()) return null;
        if (!today.isAfter(mid.get().getEndDate()))
            return dday("MIDTERM", mid.get().getStartDate(), mid.get().getEndDate(), today);
        if (fin.isEmpty() || !fin.get().getStartDate().isAfter(mid.get().getEndDate())
                || today.isAfter(fin.get().getEndDate())) return null;
        return dday("FINAL", fin.get().getStartDate(), fin.get().getEndDate(), today);
    }

    private Optional<SchoolCalendarEvent> uniqueExam(Term term, AcademicEventType type) {
        LocalDate limit = term.vacation() != null ? term.vacation()
                : LocalDate.of(term.year() + (term.semester() == 2 ? 1 : 0), term.semester() == 1 ? 9 : 3, 1);
        List<SchoolCalendarEvent> matching = term.events().stream()
                .filter(e -> effectiveType(e, term.year(), term.semester()) == type)
                .filter(e -> !e.getStartDate().isBefore(term.start())
                        && !e.getEndDate().isBefore(e.getStartDate())
                        && e.getEndDate().isBefore(limit)).toList();
        return matching.size() == 1 ? Optional.of(matching.get(0)) : Optional.empty();
    }

    private AcademicEventType effectiveType(SchoolCalendarEvent event, int year, int semester) {
        if ("HANSEO".equals(event.getSource())) return event.getEventType();
        if (!"MANUAL".equals(event.getSource()) || event.getAcademicYear() != null
                || event.getSemester() != null || event.getEventType() != AcademicEventType.OTHER) {
            return AcademicEventType.OTHER;
        }
        String title = event.getTitle().replaceAll("[\\s\\p{Z}]+", "");
        var exam = LEGACY_EXAM_TITLE.matcher(title);
        if (exam.matches()) {
            if ((exam.group(1) != null && Integer.parseInt(exam.group(1)) != year)
                    || (exam.group(2) != null && Integer.parseInt(exam.group(2)) != semester)) {
                return AcademicEventType.OTHER;
            }
            return "중간고사".equals(exam.group(3)) ? AcademicEventType.MIDTERM : AcademicEventType.FINAL;
        }
        String vacation = semester == 1 ? "하계방학" : "동계방학";
        if (event.getStartDate().equals(event.getEndDate())
                && title.matches(vacation + "(?:시작|시작일)?")) {
            return AcademicEventType.VACATION_START;
        }
        return AcademicEventType.OTHER;
    }

    private AcademicDday dday(String type, LocalDate start, LocalDate end, LocalDate today) {
        return new AcademicDday(type, start, end, Math.max(0, ChronoUnit.DAYS.between(today, start)));
    }

    public record Term(int year, int semester, LocalDate start, LocalDate vacation,
                       List<SchoolCalendarEvent> events) {}
}
