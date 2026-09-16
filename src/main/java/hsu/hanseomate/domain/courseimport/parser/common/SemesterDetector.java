package hsu.hanseomate.domain.courseimport.parser.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds one unambiguous academic year and semester across all workbook sources. */
public final class SemesterDetector {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("학년도\\s*(?:[:：|,/;·-]\\s*)?(?<year>20\\d{2})\\s*"
                    + "(?:[:：|,/;·-]\\s*)?학기\\s*(?:[:：|,/;·-]\\s*)?"
                    + "(?:제\\s*)?(?<semester>[12])"),
            Pattern.compile("학기\\s*(?:[:：|,/;·-]\\s*)?(?:제\\s*)?(?<semester>[12])\\s*"
                    + "(?:[:：|,/;·-]\\s*)?학년도\\s*(?:[:：|,/;·-]\\s*)?"
                    + "(?<year>20\\d{2})"),
            Pattern.compile("(?<!\\d)(?<year>20\\d{2})\\s*(?:학년도|년도|년)?\\s*"
                    + "(?:제\\s*)?(?<semester>[12])\\s*학기"),
            Pattern.compile("(?<!\\d)(?<year>20\\d{2})\\s*[-._/]\\s*(?<semester>[12])"
                    + "(?!\\d)(?!\\s*[-._/]\\s*\\d)"),
            Pattern.compile("(?<!\\d)(?:제\\s*)?(?<semester>[12])\\s*학기\\s*[-._/]?\\s*"
                    + "(?<year>20\\d{2})\\s*(?:학년도|년도|년)?(?!\\d)"),
            Pattern.compile("(?<!\\d)(?<semester>[12])\\s*[-._/]\\s*(?<year>20\\d{2})(?!\\d)")
    );

    private SemesterDetector() {
    }

    public static SemesterInfo detect(List<SheetView> views, String fileName) {
        List<Candidate> candidates = new ArrayList<>();
        for (SheetView view : views) {
            List<RowText> recentRows = new ArrayList<>();
            for (int row = 1; row <= view.maxRow(); row++) {
                for (int column = 1; column <= view.maxColumn(); column++) {
                    String value = view.text(row, column, false, false);
                    if (!value.isEmpty()) {
                        candidates.addAll(matches(value, "CELL", view.name(), row, column));
                    }
                }
                String rowText = String.join(" | ", view.meaningfulValues(row, false));
                if (rowText.isEmpty()) {
                    continue;
                }
                recentRows.add(new RowText(row, rowText));
                if (recentRows.size() > 4) {
                    recentRows.remove(0);
                }
                for (int depth = 1; depth <= recentRows.size(); depth++) {
                    List<RowText> windowRows = recentRows.subList(recentRows.size() - depth, recentRows.size());
                    String window = String.join(" | ", windowRows.stream().map(RowText::text).toList());
                    if (!window.contains("학년도") || !window.contains("학기")) {
                        continue;
                    }
                    candidates.addAll(matches(
                            window,
                            "CELL",
                            view.name(),
                            windowRows.get(0).row(),
                            null
                    ));
                }
            }
            candidates.addAll(matches(view.sheet().getSheetName(), "SHEET_NAME", view.name(), null, null));
        }
        candidates.addAll(matches(fileName, "FILE_NAME", null, null, null));

        Set<SemesterKey> semesters = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            semesters.add(new SemesterKey(candidate.year(), candidate.semester()));
        }
        if (semesters.size() > 1) {
            List<Map<String, Object>> grouped = semesters.stream()
                    .sorted(Comparator.comparingInt(SemesterKey::year).thenComparingInt(SemesterKey::semester))
                    .map(key -> {
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("academicYear", key.year());
                        value.put("semester", key.semester());
                        value.put("sources", candidates.stream()
                                .filter(candidate -> candidate.year() == key.year()
                                        && candidate.semester() == key.semester())
                                .map(Candidate::sourceDetails)
                                .toList());
                        return value;
                    })
                    .toList();
            throw new CourseWorkbookParseException(
                    "SEMESTER_CONFLICT",
                    "엑셀 내부와 파일 이름에서 서로 다른 학기 정보가 발견되었습니다.",
                    Map.of("semesters", grouped)
            );
        }
        if (!semesters.isEmpty()) {
            SemesterKey semester = semesters.iterator().next();
            return new SemesterInfo(semester.year(), semester.semester());
        }
        throw new CourseWorkbookParseException(
                "SEMESTER_NOT_FOUND",
                "엑셀 내부 제목에서 학년도와 학기를 찾을 수 없습니다."
        );
    }

    private static List<Candidate> matches(
            String value,
            String sourceType,
            String sheet,
            Integer row,
            Integer column
    ) {
        String normalized = ExcelText.normalize(value);
        List<Candidate> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(normalized);
            while (matcher.find()) {
                int year = Integer.parseInt(matcher.group("year"));
                int semester = Integer.parseInt(matcher.group("semester"));
                String key = year + ":" + semester + ":" + matcher.start() + ":" + matcher.end();
                if (seen.add(key)) {
                    result.add(new Candidate(
                            year, semester, sourceType, normalized, sheet, row, column
                    ));
                }
            }
        }
        return result;
    }

    private record Candidate(
            int year,
            int semester,
            String sourceType,
            String value,
            String sheet,
            Integer row,
            Integer column
    ) {
        Map<String, Object> sourceDetails() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("sourceType", sourceType);
            details.put("value", value.substring(0, Math.min(200, value.length())));
            if (sheet != null) details.put("sheetName", sheet);
            if (row != null) details.put("row", row);
            if (column != null) details.put("column", column);
            return details;
        }
    }

    private record RowText(int row, String text) {
    }

    private record SemesterKey(int year, int semester) {
    }
}
