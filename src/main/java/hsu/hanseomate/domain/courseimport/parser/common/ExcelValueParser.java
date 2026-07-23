package hsu.hanseomate.domain.courseimport.parser.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExcelValueParser {

    private static final Pattern NUMBER = Pattern.compile(
            "^[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)\\s*(?:학점|시간|시수)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GRADE = Pattern.compile("(?<!\\d)([1-6])\\s*학?년?");
    private static final Pattern GRADE_RANGE = Pattern.compile("([1-6])\\s*(?:~|-|–|—|부터)\\s*([1-6])");

    private ExcelValueParser() {
    }

    public static Double parseNumber(String value) {
        String normalized = ExcelText.normalize(value);
        if (normalized.isEmpty()) {
            return null;
        }
        String compact = normalized.replace(",", "");
        if (!NUMBER.matcher(compact).matches()) {
            return null;
        }
        Matcher matcher = Pattern.compile("^[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)").matcher(compact);
        if (!matcher.find()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(matcher.group());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Integer parseInteger(String value) {
        Double number = parseNumber(value);
        if (number == null || number % 1 != 0) {
            return null;
        }
        return number.intValue();
    }

    public static GradeValue parseTargetGrade(String value) {
        String normalized = ExcelText.normalize(value);
        if (normalized.isEmpty()) {
            return new GradeValue(null, false);
        }
        String compact = normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (Set.of("공통", "전학년", "전체학년", "전체", "all").contains(compact)) {
            return new GradeValue(null, true);
        }
        Integer integer = parseInteger(normalized);
        if (integer != null && integer >= 1 && integer <= 6) {
            return new GradeValue(integer, false);
        }
        Matcher matcher = Pattern.compile("([1-6])학?년?").matcher(compact);
        return matcher.matches()
                ? new GradeValue(Integer.valueOf(matcher.group(1)), false)
                : new GradeValue(null, false);
    }

    public static boolean parseTeamTeaching(String value) {
        String compact = ExcelText.normalize(value)
                .replaceAll("[\\s._-]+", "")
                .toLowerCase(Locale.ROOT);
        if (Set.of("n", "no", "false", "x", "×", "없음", "해당없음", "미실시", "단독", "아니오", "0")
                .contains(compact)) {
            return false;
        }
        return Set.of("y", "yes", "true", "o", "○", "있음", "유", "실시", "팀티칭", "공동강의", "공동", "1")
                .contains(compact)
                || compact.contains("팀티칭")
                || compact.contains("공동강의");
    }

    public static List<Integer> parseAllowedGrades(String value) {
        return parseAllowedGrades(value, 4);
    }

    public static List<Integer> parseAllowedGrades(String value, int maximum) {
        if (maximum < 1 || maximum > 6) {
            throw new IllegalArgumentException("maximum must be between 1 and 6");
        }
        String compact = ExcelText.normalize(value).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (compact.isEmpty()) {
            return List.of();
        }
        List<Integer> all = range(1, maximum);
        if (containsAny(compact, "전학년", "전체학년", "모든학년", "all")) {
            return all;
        }
        LinkedHashSet<Integer> grades = new LinkedHashSet<>();
        Matcher range = GRADE_RANGE.matcher(compact);
        if (range.find()) {
            int start = Integer.parseInt(range.group(1));
            int end = Integer.parseInt(range.group(2));
            grades.addAll(range(Math.min(start, end), Math.min(maximum, Math.max(start, end))));
        } else {
            Matcher matcher = GRADE.matcher(compact);
            while (matcher.find()) {
                grades.add(Integer.valueOf(matcher.group(1)));
            }
        }
        if (grades.isEmpty()) {
            Matcher digits = Pattern.compile("(?<!\\d)([1-6])(?!\\d)").matcher(compact);
            while (digits.find()) {
                grades.add(Integer.valueOf(digits.group(1)));
            }
        }
        Matcher atLeast = Pattern.compile("([1-6])학?년?(?:이상|부터)").matcher(compact);
        if (atLeast.find()) {
            grades.clear();
            grades.addAll(range(Integer.parseInt(atLeast.group(1)), maximum));
        }
        Matcher atMost = Pattern.compile("([1-6])학?년?이하").matcher(compact);
        if (atMost.find()) {
            grades.clear();
            grades.addAll(range(1, Math.min(maximum, Integer.parseInt(atMost.group(1)))));
        }
        List<Integer> valid = grades.stream().filter(valueNumber -> valueNumber >= 1 && valueNumber <= maximum)
                .sorted().toList();
        if (containsAny(compact, "불가능", "수강불가", "제외", "금지", "불허", "수강할수없")
                && !valid.isEmpty()) {
            return all.stream().filter(valueNumber -> !valid.contains(valueNumber)).toList();
        }
        return valid;
    }

    private static List<Integer> range(int start, int end) {
        List<Integer> values = new ArrayList<>();
        for (int value = start; value <= end; value++) {
            values.add(value);
        }
        return values;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public record GradeValue(Integer targetGrade, boolean commonGrade) {
    }
}
