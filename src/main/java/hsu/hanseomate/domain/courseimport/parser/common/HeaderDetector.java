package hsu.hanseomate.domain.courseimport.parser.common;

import hsu.hanseomate.domain.courseimport.dto.SourceCellRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Flexible one-to-four-row timetable header detector. */
public final class HeaderDetector {

    public static final int MAX_HEADER_ROWS = 4;

    private static final Map<String, List<String>> ALIASES = aliases();
    private static final Map<String, String> SOURCE_FIELD_NAMES = Map.ofEntries(
            Map.entry("course_code", "courseCode"),
            Map.entry("course_name", "courseName"),
            Map.entry("section_no", "sectionNo"),
            Map.entry("grade", "gradeText"),
            Map.entry("credit", "creditText"),
            Map.entry("class_hours", "classHoursText"),
            Map.entry("instructor", "instructorName"),
            Map.entry("schedule", "scheduleText"),
            Map.entry("classroom", "classroomText"),
            Map.entry("team_teaching", "teamTeachingText"),
            Map.entry("note", "note"),
            Map.entry("eligibility", "eligibilityText"),
            Map.entry("area", "areaText"),
            Map.entry("classification", "classificationText"),
            Map.entry("academic_unit", "academicUnitText"),
            Map.entry("department", "departmentText"),
            Map.entry("major", "majorText"),
            Map.entry("general_category", "generalCategoryText"),
            Map.entry("category_level", "categoryLevelText"),
            Map.entry("provider", "providerText")
    );
    private static final Set<String> HEADER_FRAGMENTS = Set.of(
            "코드", "명", "번호", "학년", "분반", "학점", "시수", "교수", "시간", "교시",
            "장소", "강의실", "여부", "비고", "학과", "영역", "구분", "제목", "연차", "단위",
            "교원", "강사", "강의장", "메모", "유무", "기관", "주체", "카테고리", "레벨", "단계",
            "학부", "전공", "대분류", "중분류", "소분류"
    );
    private static final Set<String> STRONG_SUPPORTING_FIELDS = Set.of(
            "grade", "section_no", "credit", "class_hours", "instructor",
            "schedule", "classroom", "team_teaching"
    );
    private static final Set<String> FUTURE_HEADER_LABELS = Set.of(
            "대상", "제목", "명칭", "이름", "식별", "식별값", "번호", "코드", "반", "단위", "단위수",
            "담당", "담당자", "일정", "장소", "협업", "공동", "설명", "주석", "제공자"
    );
    private static final Set<String> SUMMARY_IDENTITIES = Set.of(
            "total", "subtotal", "합계", "소계", "총계", "전체"
    );
    private static final Pattern APPENDIX_MARKER = Pattern.compile("(?:부록|참고|통계|집계|요약|현황|합계|소계|총계)");
    private static final Pattern DATA_GRADE = Pattern.compile("^[1-6]\\s*학년$|^(?:공통|전학년)$");
    private static final Pattern DATA_NUMBER = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");
    private static final Pattern DATA_SCHEDULE = Pattern.compile("[월화수목금토일](?:요일)?\\s*\\d");

    private HeaderDetector() {
    }

    public static String canonicalHeader(String value) {
        String compact = ExcelText.compact(value);
        if (compact.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            if (entry.getValue().contains(compact)) {
                return entry.getKey();
            }
        }
        for (Map.Entry<String, List<String>> entry : ALIASES.entrySet()) {
            if (entry.getValue().stream().anyMatch(alias -> alias.length() >= 4 && compact.endsWith(alias))) {
                return entry.getKey();
            }
        }
        return semanticHeader(compact);
    }

    public static HeaderMap detectHeader(SheetView view, int rowNumber) {
        HeaderParts single = headerParts(view, List.of(rowNumber));
        if (isComplete(single.columns())
                && !looksLikeAppendixTable(view, rowNumber, rowNumber, single.columns())) {
            return new HeaderMap(rowNumber, rowNumber, single.columns(), single.sourceHeaders());
        }
        if (rowNumber <= 1 || !looksLikeHeaderContinuation(view, rowNumber)) {
            return null;
        }

        int maxDepth = Math.min(MAX_HEADER_ROWS, rowNumber);
        for (int depth = 2; depth <= maxDepth; depth++) {
            int start = rowNumber - depth + 1;
            List<Integer> rows = range(start, rowNumber);
            List<Integer> previousRows = rows.subList(0, rows.size() - 1);
            HeaderParts previous = headerParts(view, previousRows);
            if (isComplete(previous.columns())
                    && looksLikeDataBelowHeader(view, rowNumber, previous.columns())) {
                continue;
            }
            HeaderParts combined = headerParts(view, rows);
            if (!isComplete(combined.columns())) {
                continue;
            }
            if (isComplete(previous.columns())
                    && previous.columns().keySet().containsAll(combined.columns().keySet())) {
                continue;
            }
            if (looksLikeAppendixTable(view, start, rowNumber, combined.columns())) {
                continue;
            }
            return new HeaderMap(start, rowNumber, combined.columns(), combined.sourceHeaders());
        }
        return null;
    }

    public static boolean looksLikeUnsupportedHeader(
            SheetView view,
            int rowNumber,
            HeaderMap activeHeader
    ) {
        List<ColumnValue> values = new ArrayList<>();
        for (int column = 1; column <= view.maxColumn(); column++) {
            String value = view.text(rowNumber, column, false, false);
            if (!value.isEmpty()) {
                values.add(new ColumnValue(column, value));
            }
        }
        Set<String> canonical = new LinkedHashSet<>();
        for (ColumnValue value : values) {
            String matched = canonicalHeader(value.value());
            if (matched != null) {
                canonical.add(matched);
            }
        }
        if (canonical.size() >= 2
                && (canonical.contains("course_code") || canonical.contains("course_name"))) {
            return true;
        }
        if (values.size() < 4) {
            return false;
        }
        long headerLike = values.stream().filter(item -> isFutureHeaderLabel(item.value())).count();
        boolean knownShape = headerLike >= 4 && headerLike * 10 >= values.size() * 7L;
        boolean denseUnknown = looksLikeDenseUnknownHeader(values.stream().map(ColumnValue::value).toList());
        if (!knownShape && !denseUnknown) {
            return false;
        }
        int dataSignals = activeHeader == null
                ? genericRowDataSignals(values.stream().map(ColumnValue::value).toList())
                : activeRowDataSignals(view, rowNumber, activeHeader);
        return dataSignals < 2 && hasFollowingDataShape(view, rowNumber);
    }

    public static List<SourceCellRequest> extractSourceCells(
            SheetView view,
            int rowNumber,
            HeaderMap header
    ) {
        Map<Integer, String> canonicalByColumn = new LinkedHashMap<>();
        header.columns().forEach((field, column) -> canonicalByColumn.put(column, field));
        List<SourceCellRequest> result = new ArrayList<>();
        header.sourceHeaders().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String canonical = canonicalByColumn.get(entry.getKey());
                    boolean identifier = "course_code".equals(canonical) || "section_no".equals(canonical);
                    result.add(new SourceCellRequest(
                            entry.getKey(),
                            entry.getValue(),
                            canonical == null ? null : SOURCE_FIELD_NAMES.get(canonical),
                            view.sourceText(rowNumber, entry.getKey(), identifier, true)
                    ));
                });
        return result;
    }

    private static HeaderParts headerParts(SheetView view, List<Integer> rows) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        Map<Integer, String> sourceHeaders = new LinkedHashMap<>();
        boolean resolveMerged = rows.size() > 1;
        for (int column = 1; column <= view.maxColumn(); column++) {
            LinkedHashSet<String> sourceParts = new LinkedHashSet<>();
            LinkedHashSet<String> normalizedParts = new LinkedHashSet<>();
            for (int row : rows) {
                String source = view.sourceText(row, column, false, resolveMerged);
                String normalized = view.text(row, column, false, resolveMerged);
                if (source != null) {
                    sourceParts.add(source);
                }
                if (!normalized.isEmpty()) {
                    normalizedParts.add(normalized);
                }
            }
            if (sourceParts.isEmpty()) {
                continue;
            }
            sourceHeaders.put(column, String.join(" / ", sourceParts));
            String canonical = normalizedParts.stream()
                    .map(HeaderDetector::canonicalHeader)
                    .filter(value -> value != null)
                    .findFirst()
                    .orElseGet(() -> canonicalHeader(String.join("", normalizedParts)));
            if (canonical != null) {
                columns.putIfAbsent(canonical, column);
            }
        }
        return new HeaderParts(columns, sourceHeaders);
    }

    private static boolean isComplete(Map<String, Integer> columns) {
        int identities = (columns.containsKey("course_code") ? 1 : 0)
                + (columns.containsKey("course_name") ? 1 : 0);
        if (identities == 0) {
            return false;
        }
        if (identities == 2) {
            return columns.size() >= 4;
        }
        long supporting = STRONG_SUPPORTING_FIELDS.stream().filter(columns::containsKey).count();
        return supporting >= 4;
    }

    private static boolean looksLikeHeaderContinuation(SheetView view, int rowNumber) {
        int matches = 0;
        int meaningful = 0;
        for (int column = 1; column <= view.maxColumn(); column++) {
            String value = view.text(rowNumber, column, false, false);
            if (value.isEmpty()) {
                continue;
            }
            meaningful++;
            if (canonicalHeader(value) != null || HEADER_FRAGMENTS.contains(ExcelText.compact(value))) {
                matches++;
            }
        }
        return matches >= 2 && meaningful > 0 && matches * 4 >= meaningful * 3;
    }

    private static boolean looksLikeDataBelowHeader(
            SheetView view,
            int rowNumber,
            Map<String, Integer> previousColumns
    ) {
        Integer codeColumn = previousColumns.get("course_code");
        Integer nameColumn = previousColumns.get("course_name");
        List<String> identities = new ArrayList<>();
        if (codeColumn != null) {
            String code = view.text(rowNumber, codeColumn, true, true);
            if (!code.isEmpty()) {
                identities.add(code);
            }
        }
        if (nameColumn != null) {
            String name = view.text(rowNumber, nameColumn, false, true);
            if (!name.isEmpty()) {
                identities.add(name);
            }
        }
        return !identities.isEmpty() && identities.stream().allMatch(value -> canonicalHeader(value) == null);
    }

    private static boolean looksLikeAppendixTable(
            SheetView view,
            int startRow,
            int endRow,
            Map<String, Integer> columns
    ) {
        Set<String> signals = Set.of("grade", "section_no", "class_hours", "schedule", "classroom");
        if (signals.stream().anyMatch(columns::containsKey)) {
            return false;
        }
        for (int row = startRow - 1; row >= Math.max(1, startRow - 5); row--) {
            List<String> values = view.meaningfulValues(row, false);
            if (values.isEmpty()) {
                continue;
            }
            if (values.size() == 1 && APPENDIX_MARKER.matcher(ExcelText.compact(values.get(0))).find()) {
                return true;
            }
            break;
        }
        for (int row = endRow + 1; row <= Math.min(view.maxRow(), endRow + 5); row++) {
            List<String> values = view.meaningfulValues(row, false);
            if (values.isEmpty()) {
                continue;
            }
            for (String field : List.of("course_code", "course_name")) {
                Integer column = columns.get(field);
                if (column != null
                        && SUMMARY_IDENTITIES.contains(ExcelText.compact(view.text(row, column, true, true)))) {
                    return true;
                }
            }
            break;
        }
        return false;
    }

    private static boolean isFutureHeaderLabel(String value) {
        String compact = ExcelText.compact(value);
        if (compact.isEmpty()) {
            return false;
        }
        if (canonicalHeader(value) != null || HEADER_FRAGMENTS.contains(compact)
                || FUTURE_HEADER_LABELS.contains(compact)) {
            return true;
        }
        return compact.length() <= 12 && List.of(
                "대상", "제목", "명칭", "식별값", "단위수", "담당자", "일정", "장소", "협업", "설명", "주석"
        ).stream().anyMatch(compact::endsWith);
    }

    private static boolean looksLikeDenseUnknownHeader(List<String> values) {
        if (values.size() < 6) {
            return false;
        }
        int shortTextCount = 0;
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String compact = ExcelText.compact(value);
            if (!compact.isEmpty()) {
                unique.add(compact);
            }
            boolean alphabetic = compact.chars().anyMatch(Character::isLetter);
            if (!compact.isEmpty() && compact.length() <= 16 && alphabetic
                    && !DATA_NUMBER.matcher(compact).matches()
                    && !DATA_GRADE.matcher(value).matches()
                    && !DATA_SCHEDULE.matcher(value).find()
                    && !looksLikeCourseIdentifier(compact)) {
                shortTextCount++;
            }
        }
        return shortTextCount * 10 >= values.size() * 9 && unique.size() >= 4;
    }

    private static int genericRowDataSignals(Collection<String> values) {
        int signals = 0;
        for (String value : values) {
            if (DATA_NUMBER.matcher(value).matches()) {
                signals++;
            }
            if (looksLikeCourseIdentifier(ExcelText.compact(value))) {
                signals++;
            }
            if (DATA_SCHEDULE.matcher(value).find()) {
                signals++;
            }
            if (DATA_GRADE.matcher(value).matches()) {
                signals++;
            }
        }
        return signals;
    }

    private static int activeRowDataSignals(SheetView view, int row, HeaderMap header) {
        int signals = 0;
        Integer codeColumn = header.get("course_code");
        if (codeColumn != null
                && looksLikeCourseIdentifier(ExcelText.compact(view.text(row, codeColumn, true, true)))) {
            signals++;
        }
        for (String field : List.of("section_no", "credit", "class_hours")) {
            Integer column = header.get(field);
            if (column != null && DATA_NUMBER.matcher(view.text(row, column, false, true)).matches()) {
                signals++;
            }
        }
        Integer gradeColumn = header.get("grade");
        if (gradeColumn != null && DATA_GRADE.matcher(view.text(row, gradeColumn, false, true)).matches()) {
            signals++;
        }
        Integer scheduleColumn = header.get("schedule");
        if (scheduleColumn != null
                && DATA_SCHEDULE.matcher(view.text(row, scheduleColumn, false, true)).find()) {
            signals++;
        }
        return signals;
    }

    private static boolean hasFollowingDataShape(SheetView view, int rowNumber) {
        for (int row = rowNumber + 1; row <= Math.min(view.maxRow(), rowNumber + 4); row++) {
            List<String> values = view.meaningfulValues(row, false);
            if (values.isEmpty()) {
                continue;
            }
            long headerLike = values.stream().filter(HeaderDetector::isFutureHeaderLabel).count();
            if (headerLike * 2 >= values.size()) {
                return false;
            }
            long numeric = values.stream().filter(value -> DATA_NUMBER.matcher(value).matches()).count();
            long identifiers = values.stream()
                    .map(ExcelText::compact)
                    .filter(HeaderDetector::looksLikeCourseIdentifier)
                    .count();
            long schedules = values.stream().filter(value -> DATA_SCHEDULE.matcher(value).find()).count();
            return numeric >= 2 || (identifiers >= 1 && schedules >= 1);
        }
        return false;
    }

    private static boolean looksLikeCourseIdentifier(String compact) {
        return compact.length() >= 4
                && compact.chars().anyMatch(Character::isDigit)
                && compact.matches("[0-9a-z]+");
    }

    private static String semanticHeader(String compact) {
        if (containsAny(compact, "교수", "교원", "강사", "담당자")
                && (compact.contains("담당") || containsAny(compact, "성명", "이름", "명"))) {
            return "instructor";
        }
        boolean course = containsAny(compact, "교과목", "교과", "과목", "강좌");
        if (course && containsAny(compact, "코드", "번호", "식별")) return "course_code";
        if (course && (containsAny(compact, "명칭", "이름", "제목") || compact.endsWith("명"))) return "course_name";
        if (compact.contains("분반") || (compact.contains("강좌") && compact.contains("반"))) return "section_no";
        if (containsAny(compact, "학년", "연차")) return "grade";
        if (compact.contains("학점") || (compact.contains("이수") && compact.contains("단위"))) return "credit";
        if (containsAny(compact, "시수", "수업시간수", "강의시간수")) return "class_hours";
        if ((containsAny(compact, "요일", "일시") && containsAny(compact, "교시", "시간", "일시"))
                || Set.of("수업일정", "강의일정").contains(compact)) return "schedule";
        if (containsAny(compact, "강의실", "강의장", "수업장소", "강의장소")) return "classroom";
        if (containsAny(compact, "팀티칭", "공동강의")) return "team_teaching";
        if (containsAny(compact, "비고", "메모", "특이사항", "참고사항")) return "note";
        if (compact.contains("수강") && containsAny(compact, "학과", "대상", "제한", "조건")) return "eligibility";
        if (compact.contains("학과") && compact.contains("전공") && !compact.contains("수강")) return "academic_unit";
        if (containsAny(compact, "개설학과", "소속학과", "주관학과", "학과명")) return "department";
        if (containsAny(compact, "개설전공", "소속전공", "주관전공", "전공명")) return "major";
        if (containsAny(compact, "운영기관", "제공기관", "운영주체", "플랫폼")) return "provider";
        if (containsAny(compact, "분류단계", "분류레벨", "카테고리단계")) return "category_level";
        if (containsAny(compact, "세부분류", "소분류", "중분류", "대분류", "트랙")) return "general_category";
        if (compact.contains("교양") && containsAny(compact, "구분", "유형", "필수선택")) return "classification";
        if (compact.contains("영역")) return "area";
        return null;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> range(int start, int end) {
        List<Integer> result = new ArrayList<>();
        for (int value = start; value <= end; value++) {
            result.add(value);
        }
        return result;
    }

    private static Map<String, List<String>> aliases() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("course_code", List.of("교과목코드", "과목코드", "강좌코드", "교과목번호", "과목번호", "학수번호"));
        values.put("course_name", List.of("교과목명", "과목명", "강좌명", "강좌제목", "교과목제목", "과목제목"));
        values.put("section_no", List.of("분반", "강좌분반", "반번호"));
        values.put("grade", List.of("학년", "대상학년", "수강학년", "수강연차", "대상연차"));
        values.put("credit", List.of("학점", "이수학점", "이수단위"));
        values.put("class_hours", List.of("시수", "수업시수"));
        values.put("instructor", List.of("담당교수명", "담당교수", "교수명", "교수", "교원명", "강사명"));
        values.put("schedule", List.of("요일및시간", "요일및교시", "강의시간", "수업시간", "수업요일및시간", "수업요일및교시", "수업요일교시"));
        values.put("classroom", List.of("강의실", "수업장소", "강의장소", "강의장"));
        values.put("team_teaching", List.of("팀티칭여부", "팀티칭", "공동강의여부", "공동강의유무"));
        values.put("note", List.of("비고", "특이사항", "메모"));
        values.put("eligibility", List.of("수강학과", "수강대상학과", "수강대상", "수강제한", "수강학과(또는비고)", "수강학과또는비고"));
        values.put("area", List.of("영역", "교양영역"));
        values.put("classification", List.of("이수구분", "교양구분"));
        values.put("academic_unit", List.of("학과전공", "학과/전공", "학과·전공", "개설학과전공", "소속학과전공"));
        values.put("department", List.of("학과", "학과명", "개설학과", "소속학과"));
        values.put("major", List.of("전공", "전공명", "개설전공", "소속전공"));
        values.put("general_category", List.of("교양카테고리", "교양분류", "대분류", "중분류", "소분류", "세부분류"));
        values.put("category_level", List.of("교양분류단계", "카테고리단계", "분류단계", "카테고리레벨", "분류레벨"));
        values.put("provider", List.of("운영기관", "제공기관", "강좌제공기관", "수업제공기관", "운영주체"));
        Map<String, List<String>> compact = new LinkedHashMap<>();
        values.forEach((key, list) -> compact.put(key, list.stream().map(ExcelText::compact).toList()));
        return compact;
    }

    private record HeaderParts(Map<String, Integer> columns, Map<Integer, String> sourceHeaders) {
    }

    private record ColumnValue(int column, String value) {
    }
}
