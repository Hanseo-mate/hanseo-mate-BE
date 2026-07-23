package hsu.hanseomate.domain.courseimport.parser.common;

import hsu.hanseomate.domain.courseimport.dto.ClassroomRequest;
import hsu.hanseomate.domain.courseimport.dto.ParseIssueRequest;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClassroomParser {

    private static final Pattern CAMPUS = Pattern.compile(
            "^\\[\\s*(?<campus>[^]\\r\\n]+?)\\s*]\\s*(?<body>.*)$"
    );
    private static final Pattern CAMPUS_MARKER = Pattern.compile("\\[[^]\\r\\n]+]");
    private static final Pattern SPACED_ROOM = Pattern.compile(
            "^(?<building>.+?)\\s+(?<room>(?:[A-Za-z]{1,4}[- ]?)?\\d{1,4}(?:-\\d{1,3})?[A-Za-z]?)(?:\\s*호)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ATTACHED_ROOM = Pattern.compile(
            "^(?<building>.+?)(?<room>(?:[A-Za-z]{1,4})?\\d{2,4}(?:-\\d{1,3})?[A-Za-z]?)(?:\\s*호)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SPECIAL = Pattern.compile(
            "(?:https?://|www\\.|e\\s*[-_]?\\s*class|사이버|온라인|원격|비대면|미정|추후공지)",
            Pattern.CASE_INSENSITIVE
    );

    private ClassroomParser() {
    }

    public static ClassroomParseResult parse(String value, String sheetName, Integer rowNumber) {
        String raw = ExcelText.normalize(value);
        List<ClassroomRequest> classrooms = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String item : splitValues(raw)) {
                ClassroomRequest classroom = parseOne(item);
                if (classroom != null) {
                    classrooms.add(classroom);
                }
            }
        }
        List<ParseIssueRequest> issues = new ArrayList<>();
        if (!raw.isEmpty() && raw.contains("[") && !CAMPUS_MARKER.matcher(raw).find()) {
            issues.add(new ParseIssueRequest(
                    IssueSeverity.WARNING,
                    "MALFORMED_CAMPUS_MARKER",
                    "강의실의 캠퍼스 표기([캠퍼스])를 해석할 수 없습니다.",
                    sheetName,
                    rowNumber,
                    "classroomText",
                    raw
            ));
        }
        return new ClassroomParseResult(classrooms, issues);
    }

    static List<String> splitValues(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> markerParts = new ArrayList<>();
        for (String line : normalized.split("[\\r\\n]+")) {
            String trimmed = trimSeparators(line);
            if (trimmed.isEmpty()) {
                continue;
            }
            List<MatcherRange> markers = new ArrayList<>();
            Matcher matcher = CAMPUS_MARKER.matcher(trimmed);
            while (matcher.find()) {
                markers.add(new MatcherRange(matcher.start(), matcher.end()));
            }
            if (markers.size() <= 1) {
                markerParts.add(trimmed);
                continue;
            }
            String prefix = trimSeparators(trimmed.substring(0, markers.get(0).start()));
            if (!prefix.isEmpty()) {
                markerParts.add(prefix);
            }
            for (int index = 0; index < markers.size(); index++) {
                int end = index + 1 < markers.size() ? markers.get(index + 1).start() : trimmed.length();
                String part = trimSeparators(trimmed.substring(markers.get(index).start(), end));
                if (!part.isEmpty()) {
                    markerParts.add(part);
                }
            }
        }
        List<String> expanded = new ArrayList<>();
        for (String part : markerParts) {
            if (CAMPUS_MARKER.matcher(part).find()) {
                expanded.add(part);
                continue;
            }
            List<String> commaParts = Pattern.compile("\\s*[,;]\\s*").splitAsStream(part)
                    .map(ClassroomParser::trimSeparators)
                    .filter(item -> !item.isEmpty())
                    .toList();
            if (commaParts.size() > 1 && commaParts.stream().allMatch(ClassroomParser::looksPhysical)) {
                expanded.addAll(commaParts);
            } else {
                expanded.add(part);
            }
        }
        return expanded;
    }

    private static ClassroomRequest parseOne(String value) {
        String original = ExcelText.normalize(value);
        if (original.isEmpty()) {
            return null;
        }
        String campusCode = null;
        String body = original;
        Matcher campus = CAMPUS.matcher(original);
        if (campus.matches()) {
            campusCode = blankToNull(campus.group("campus").trim());
            body = trimSeparators(campus.group("body"));
        }
        if (body.isEmpty() || SPECIAL.matcher(body).find()) {
            return new ClassroomRequest(campusCode, null, null, original);
        }
        Matcher room = SPACED_ROOM.matcher(body);
        if (!room.matches()) {
            room = ATTACHED_ROOM.matcher(body);
        }
        if (room.matches()) {
            return new ClassroomRequest(
                    campusCode,
                    blankToNull(trimSeparators(room.group("building"))),
                    room.group("room").replaceAll("\\s+", "").toUpperCase(),
                    original
            );
        }
        return new ClassroomRequest(campusCode, blankToNull(body), null, original);
    }

    private static boolean looksPhysical(String value) {
        Matcher campus = CAMPUS.matcher(value);
        String candidate = campus.matches() ? campus.group("body").trim() : value;
        return SPACED_ROOM.matcher(candidate).matches() || ATTACHED_ROOM.matcher(candidate).matches();
    }

    private static String trimSeparators(String value) {
        return value == null ? "" : value.replaceAll("^[\\s,;|/]+|[\\s,;|/]+$", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record MatcherRange(int start, int end) {
    }

    public record ClassroomParseResult(
            List<ClassroomRequest> classrooms,
            List<ParseIssueRequest> issues
    ) {
    }
}
