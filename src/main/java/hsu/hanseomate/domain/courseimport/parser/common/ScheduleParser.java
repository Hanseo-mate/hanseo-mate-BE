package hsu.hanseomate.domain.courseimport.parser.common;

import hsu.hanseomate.domain.courseimport.dto.ClassroomRequest;
import hsu.hanseomate.domain.courseimport.dto.CourseScheduleRequest;
import hsu.hanseomate.domain.courseimport.dto.ParseIssueRequest;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScheduleParser {

    public static final int MIN_PERIOD = 1;
    public static final int MAX_PERIOD = 30;

    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "월", DayOfWeek.MONDAY,
            "화", DayOfWeek.TUESDAY,
            "수", DayOfWeek.WEDNESDAY,
            "목", DayOfWeek.THURSDAY,
            "금", DayOfWeek.FRIDAY,
            "토", DayOfWeek.SATURDAY,
            "일", DayOfWeek.SUNDAY
    );
    private static final Pattern DAY_MARKER = Pattern.compile(
            "(?<![가-힣])(?<day>[월화수목금토일])(?:요일)?(?=\\s*(?:\\d|[,/;·:&]|$))"
    );
    private static final Pattern PERIOD_TOKEN = Pattern.compile("(?<!\\d)\\d+(?:\\s*[-~]\\s*\\d+)?(?!\\d)");
    private static final Pattern CLOCK_TIME = Pattern.compile("(?<!\\d)\\d{1,2}\\s*:\\s*\\d{2}(?!\\d)");
    private static final Pattern SPECIAL = Pattern.compile(
            "(?:cyber|e\\s*[-_]?\\s*class|사이버|온라인|원격|비대면|현장실습|집중수업|"
                    + "프리즘시스(?:템|탬)|학과자체편성|비행교육원자체편성|"
                    + "추후(?:공지)?|미정|별도\\s*(?:공지|협의)?|협의|해당\\s*없음|없음|tba)",
            Pattern.CASE_INSENSITIVE
    );

    private ScheduleParser() {
    }

    public static ScheduleParseResult parse(
            String scheduleText,
            String classroomText,
            String sheetName,
            Integer rowNumber
    ) {
        ScheduleParseResult groups = parseGroups(scheduleText, sheetName, rowNumber);
        ClassroomParser.ClassroomParseResult rooms = ClassroomParser.parse(
                classroomText,
                sheetName,
                rowNumber
        );
        List<ParseIssueRequest> issues = new ArrayList<>(groups.issues());
        issues.addAll(rooms.issues());
        List<CourseScheduleRequest> schedules = groups.schedules();
        List<ClassroomRequest> classrooms = rooms.classrooms();
        if (schedules.isEmpty() || classrooms.isEmpty()) {
            return new ScheduleParseResult(new ArrayList<>(schedules), issues);
        }

        if (schedules.size() == 1 && classrooms.size() > 1) {
            List<CourseScheduleRequest> expanded = classrooms.stream()
                    .map(room -> withClassroom(schedules.get(0), room))
                    .toList();
            return new ScheduleParseResult(new ArrayList<>(expanded), issues);
        }
        if (classrooms.size() == 1) {
            List<CourseScheduleRequest> shared = schedules.stream()
                    .map(schedule -> withClassroom(schedule, classrooms.get(0)))
                    .toList();
            return new ScheduleParseResult(new ArrayList<>(shared), issues);
        }
        if (schedules.size() != classrooms.size()) {
            issues.add(issue(
                    IssueSeverity.ERROR,
                    "SCHEDULE_CLASSROOM_COUNT_MISMATCH",
                    "요일 묶음과 강의실 수가 다릅니다: 요일 묶음 " + schedules.size()
                            + "개, 강의실 " + classrooms.size() + "개",
                    ExcelText.normalize(classroomText),
                    sheetName,
                    rowNumber,
                    "classroomText"
            ));
        }
        List<CourseScheduleRequest> paired = new ArrayList<>();
        for (int index = 0; index < schedules.size(); index++) {
            paired.add(withClassroom(
                    schedules.get(index),
                    index < classrooms.size() ? classrooms.get(index) : null
            ));
        }
        return new ScheduleParseResult(paired, issues);
    }

    public static ScheduleParseResult parseGroups(
            String value,
            String sheetName,
            Integer rowNumber
    ) {
        String raw = normalizeSchedule(value);
        if (raw == null) {
            return new ScheduleParseResult(List.of(), List.of(issue(
                    IssueSeverity.WARNING,
                    "MISSING_SCHEDULE",
                    "요일 및 시간 값이 비어 있습니다.",
                    null,
                    sheetName,
                    rowNumber,
                    "scheduleText"
            )));
        }
        String text = separateAdjacentDays(raw);
        List<Match> dayMatches = new ArrayList<>();
        Matcher dayMatcher = DAY_MARKER.matcher(text);
        while (dayMatcher.find()) {
            dayMatches.add(new Match(dayMatcher.start(), dayMatcher.end(), dayMatcher.group("day")));
        }
        if (dayMatches.isEmpty()) {
            String code = SPECIAL.matcher(text).find() || text.replaceAll("[ -./]", "").isEmpty()
                    ? "NON_CONCRETE_SCHEDULE"
                    : "UNRECOGNIZED_SCHEDULE";
            String message = "NON_CONCRETE_SCHEDULE".equals(code)
                    ? "온라인·미정·특수 수업은 구체적인 요일/교시로 변환하지 않았습니다."
                    : "구조화할 수 없는 시간 문구를 원문 그대로 보존했습니다.";
            return new ScheduleParseResult(List.of(), List.of(issue(
                    IssueSeverity.WARNING, code, message, raw, sheetName, rowNumber, "scheduleText"
            )));
        }

        List<CourseScheduleRequest> schedules = new ArrayList<>();
        List<ParseIssueRequest> issues = new ArrayList<>();
        List<DayOfWeek> pending = new ArrayList<>();
        for (int index = 0; index < dayMatches.size(); index++) {
            Match match = dayMatches.get(index);
            int end = index + 1 < dayMatches.size() ? dayMatches.get(index + 1).start() : text.length();
            String segment = text.substring(match.end(), end).replaceAll("^[\\s,;/·:&]+|[\\s,;/·:&]+$", "");
            PeriodResult periods = parsePeriodSegment(segment, raw, sheetName, rowNumber);
            issues.addAll(periods.issues());
            DayOfWeek day = DAYS.get(match.value());
            if (periods.periods().isEmpty()) {
                if (periods.issues().isEmpty() && !segment.matches(".*\\d.*")) {
                    pending.add(day);
                }
                continue;
            }
            List<DayOfWeek> effectiveDays = new ArrayList<>(pending);
            effectiveDays.add(day);
            for (DayOfWeek effectiveDay : effectiveDays) {
                schedules.add(new CourseScheduleRequest(effectiveDay, periods.periods(), null));
            }
            pending.clear();
        }
        if (!pending.isEmpty()) {
            issues.add(issue(
                    IssueSeverity.WARNING,
                    "MISSING_PERIOD",
                    "교시 없는 요일 문구를 원문 그대로 보존했습니다.",
                    raw,
                    sheetName,
                    rowNumber,
                    "scheduleText"
            ));
        }
        if (schedules.isEmpty() && issues.isEmpty()) {
            issues.add(issue(
                    IssueSeverity.WARNING,
                    "UNRECOGNIZED_SCHEDULE",
                    "구조화할 수 없는 시간 문구를 원문 그대로 보존했습니다.",
                    raw,
                    sheetName,
                    rowNumber,
                    "scheduleText"
            ));
        }
        return new ScheduleParseResult(schedules, issues);
    }

    private static PeriodResult parsePeriodSegment(
            String segment,
            String raw,
            String sheetName,
            Integer rowNumber
    ) {
        List<ParseIssueRequest> issues = new ArrayList<>();
        if (CLOCK_TIME.matcher(segment).find()) {
            issues.add(issue(
                    IssueSeverity.WARNING,
                    "NON_PERIOD_SCHEDULE",
                    "시각 범위는 교시 목록으로 자동 변환하지 않았습니다.",
                    raw,
                    sheetName,
                    rowNumber,
                    "scheduleText"
            ));
            return new PeriodResult(List.of(), issues);
        }
        String cleaned = segment.replaceAll("(?i)(?:교시|시限|限)", "");
        LinkedHashSet<Integer> periods = new LinkedHashSet<>();
        Matcher tokens = PERIOD_TOKEN.matcher(cleaned);
        while (tokens.find()) {
            String token = tokens.group().replaceAll("\\s+", "");
            if (token.contains("~") || token.contains("-")) {
                String separator = token.contains("~") ? "~" : "-";
                String[] values = token.split(Pattern.quote(separator), 2);
                Integer start = parsePeriodNumber(values[0]);
                Integer end = parsePeriodNumber(values[1]);
                if (start == null || end == null) {
                    issues.add(issue(
                            IssueSeverity.ERROR,
                            "INVALID_PERIOD",
                            "교시 숫자가 너무 크거나 올바른 정수가 아닙니다: " + token,
                            raw,
                            sheetName,
                            rowNumber,
                            "scheduleText"
                    ));
                    continue;
                }
                if (!validPeriod(start) || !validPeriod(end)) {
                    int invalid = !validPeriod(start) ? start : end;
                    issues.add(issue(
                            IssueSeverity.ERROR,
                            "INVALID_PERIOD",
                            "교시는 " + MIN_PERIOD + "~" + MAX_PERIOD + " 범위여야 합니다: " + invalid,
                            raw,
                            sheetName,
                            rowNumber,
                            "scheduleText"
                    ));
                    continue;
                }
                if (start > end) {
                    issues.add(issue(
                            IssueSeverity.ERROR,
                            "INVALID_PERIOD_RANGE",
                            "교시 범위의 시작이 끝보다 큽니다: " + token,
                            raw,
                            sheetName,
                            rowNumber,
                            "scheduleText"
                    ));
                    continue;
                }
                for (int period = start; period <= end; period++) {
                    periods.add(period);
                }
                continue;
            }
            Integer period = parsePeriodNumber(token);
            if (period == null) {
                issues.add(issue(
                        IssueSeverity.ERROR,
                        "INVALID_PERIOD",
                        "교시 숫자가 너무 크거나 올바른 정수가 아닙니다: " + token,
                        raw,
                        sheetName,
                        rowNumber,
                        "scheduleText"
                ));
                continue;
            }
            if (!validPeriod(period)) {
                issues.add(issue(
                        IssueSeverity.ERROR,
                        "INVALID_PERIOD",
                        "교시는 " + MIN_PERIOD + "~" + MAX_PERIOD + " 범위여야 합니다: " + period,
                        raw,
                        sheetName,
                        rowNumber,
                        "scheduleText"
                ));
            } else {
                periods.add(period);
            }
        }
        return new PeriodResult(new ArrayList<>(periods), issues);
    }

    private static Integer parsePeriodNumber(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static CourseScheduleRequest withClassroom(
            CourseScheduleRequest schedule,
            ClassroomRequest classroom
    ) {
        return new CourseScheduleRequest(schedule.dayOfWeek(), schedule.periods(), classroom);
    }

    private static String normalizeSchedule(String value) {
        String text = ExcelText.normalize(value);
        if (text.isEmpty()) {
            return null;
        }
        return text.replace('，', ',')
                .replace('、', ',')
                .replace('～', '~')
                .replace('∼', '~')
                .replace('–', '-')
                .replace('—', '-');
    }

    private static String separateAdjacentDays(String value) {
        String text = value;
        String previous;
        do {
            previous = text;
            text = text.replaceAll(
                    "(?<![가-힣])([월화수목금토일])(?=[월화수목금토일](?:요일)?\\s*\\d)",
                    "$1,"
            );
        } while (!text.equals(previous));
        return text;
    }

    private static boolean validPeriod(int value) {
        return value >= MIN_PERIOD && value <= MAX_PERIOD;
    }

    private static ParseIssueRequest issue(
            IssueSeverity severity,
            String code,
            String message,
            String raw,
            String sheet,
            Integer row,
            String field
    ) {
        return new ParseIssueRequest(severity, code, message, sheet, row, field, raw);
    }

    private record PeriodResult(List<Integer> periods, List<ParseIssueRequest> issues) {
    }

    private record Match(int start, int end, String value) {
    }

    public record ScheduleParseResult(
            List<CourseScheduleRequest> schedules,
            List<ParseIssueRequest> issues
    ) {
    }
}
