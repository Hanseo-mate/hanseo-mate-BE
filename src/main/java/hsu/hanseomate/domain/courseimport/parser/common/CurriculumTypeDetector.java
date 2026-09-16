package hsu.hanseomate.domain.courseimport.parser.common;

import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Detects major/general workbooks without blindly trusting the selected endpoint. */
public final class CurriculumTypeDetector {

    private static final List<String> DOCUMENT_TERMS = List.of(
            "시간표", "편성표", "개설현황", "개설내역", "개설목록", "강좌목록"
    );
    private static final List<String> COURSE_TERMS = List.of("강좌", "강의", "교과목", "교과", "과목", "교육과정");
    private static final List<String> MAJOR_TITLE_ALIASES = List.of(
            "전공강좌강의시간표", "전공강의시간표", "전공교과목시간표", "전공교과목편성표",
            "전공교과목개설현황", "전공과목시간표", "전공과목편성표", "전공교육과정"
    );
    private static final List<String> GENERAL_TITLE_ALIASES = List.of(
            "교양교육과정", "교양강좌강의시간표", "교양강의시간표", "교양교과목시간표",
            "교양교과목편성표", "교양교과목개설현황", "교양과목시간표", "교양과목편성표"
    );
    private static final Set<String> GENERAL_SECTIONS = Set.of("교양필수", "교양선택", "필수교양", "선택교양");
    private static final Set<String> GENERAL_HEADER_FIELDS = Set.of("class_hours", "eligibility", "area", "classification");
    private static final List<String> UNIT_SUFFIXES = List.of("학과", "학부", "전공", "대학", "학군단");
    private static final List<String> UNIT_EXCLUSIONS = List.of("안내", "주의", "필독", "비고", "수강", "대상");
    private static final Pattern NON_UNIT_GROUP = Pattern.compile("^(?:전체|전|타|모든)(?:학과|학부|전공)$");

    private CurriculumTypeDetector() {
    }

    public static CurriculumType detect(
            List<SheetView> views,
            CurriculumType requested,
            String fileName
    ) {
        Evidence evidence = scan(views, fileName);
        List<CurriculumType> strongTypes = List.of(CurriculumType.values()).stream()
                .filter(type -> !evidence.strong.get(type).isEmpty())
                .toList();
        if (strongTypes.size() > 1) {
            throw new CourseWorkbookParseException(
                    "MIXED_CURRICULUM_WORKBOOK",
                    "한 파일에서 전공과 교양 교육과정의 명확한 표식이 모두 발견되었습니다.",
                    evidence.details()
            );
        }
        CurriculumType detected;
        if (strongTypes.size() == 1) {
            detected = strongTypes.get(0);
        } else {
            int major = evidence.supportScores.get(CurriculumType.MAJOR);
            int general = evidence.supportScores.get(CurriculumType.GENERAL_EDUCATION);
            if (major == general) {
                throw new CourseWorkbookParseException(
                        "CURRICULUM_TYPE_NOT_DETECTED",
                        "전공 또는 교양 시간표인지 자동으로 판단할 수 없습니다.",
                        evidence.details()
                );
            }
            detected = major > general ? CurriculumType.MAJOR : CurriculumType.GENERAL_EDUCATION;
        }
        if (requested != null && requested != detected) {
            Map<String, Object> details = new LinkedHashMap<>(evidence.details());
            details.put("requested", requested.name());
            details.put("detected", detected.name());
            throw new CourseWorkbookParseException(
                    "CURRICULUM_TYPE_MISMATCH",
                    "요청한 API 종류와 엑셀 내부 교육과정 종류가 일치하지 않습니다.",
                    details
            );
        }
        return detected;
    }

    private static Evidence scan(List<SheetView> views, String fileName) {
        Evidence evidence = new Evidence();
        if (fileName != null) {
            for (CurriculumType type : explicitNameTypes(fileName)) {
                evidence.strong.get(type).add(Map.of("kind", "FILE_NAME_MARKER", "value", clipped(fileName)));
            }
        }
        for (SheetView view : views) {
            Set<Integer> seenHeaders = new LinkedHashSet<>();
            for (CurriculumType type : explicitNameTypes(view.name())) {
                evidence.strong.get(type).add(Map.of(
                        "sheetName", view.name(), "kind", "SHEET_NAME_MARKER", "value", clipped(view.name())
                ));
            }
            for (int row = 1; row <= view.maxRow(); row++) {
                List<String> values = view.meaningfulValues(row, false);
                for (String value : values) {
                    String compact = ExcelText.compact(value);
                    for (CurriculumType type : CurriculumType.values()) {
                        if (isTitle(compact, type)) {
                            evidence.strong.get(type).add(item(view, row, "TITLE", value));
                        }
                    }
                }
                if (values.size() == 1 && GENERAL_SECTIONS.contains(ExcelText.compact(values.get(0)))) {
                    evidence.strong.get(CurriculumType.GENERAL_EDUCATION)
                            .add(item(view, row, "GENERAL_SECTION", values.get(0)));
                }
                HeaderMap header = HeaderDetector.detectHeader(view, row);
                if (header != null && seenHeaders.add(header.startRow())) {
                    addHeaderEvidence(evidence, view, header);
                }
            }
        }
        return evidence;
    }

    private static void addHeaderEvidence(Evidence evidence, SheetView view, HeaderMap header) {
        evidence.genericHeaderCount++;
        Set<String> fields = header.columns().keySet();
        List<String> majorContext = fields.stream()
                .filter(Set.of("academic_unit", "department", "major")::contains)
                .sorted().toList();
        if (!majorContext.isEmpty()) {
            evidence.supportScores.merge(CurriculumType.MAJOR, 4 + majorContext.size(), Integer::sum);
            evidence.support.get(CurriculumType.MAJOR).add(item(view, header.startRow(), "MAJOR_CONTEXT_HEADER", null));
        }
        if (fields.contains("team_teaching")) {
            evidence.supportScores.merge(CurriculumType.MAJOR, 4, Integer::sum);
            evidence.support.get(CurriculumType.MAJOR).add(item(view, header.startRow(), "MAJOR_SPECIFIC_HEADER", null));
        }
        List<String> general = fields.stream().filter(GENERAL_HEADER_FIELDS::contains).sorted().toList();
        if (!general.isEmpty()) {
            evidence.supportScores.merge(CurriculumType.GENERAL_EDUCATION, 2 + general.size(), Integer::sum);
            evidence.support.get(CurriculumType.GENERAL_EDUCATION)
                    .add(item(view, header.startRow(), "GENERAL_SPECIFIC_HEADER", null));
        }
        for (int row = Math.max(1, header.startRow() - 5); row < header.startRow(); row++) {
            List<String> values = view.meaningfulValues(row, false);
            if (values.size() == 1 && looksLikeAcademicUnit(values.get(0))) {
                evidence.supportScores.merge(CurriculumType.MAJOR, 3, Integer::sum);
                evidence.support.get(CurriculumType.MAJOR)
                        .add(item(view, row, "ACADEMIC_UNIT_BEFORE_HEADER", values.get(0)));
                break;
            }
        }
    }

    private static boolean isTitle(String compact, CurriculumType type) {
        if (compact.isEmpty() || compact.length() > 160) {
            return false;
        }
        List<String> aliases = type == CurriculumType.MAJOR ? MAJOR_TITLE_ALIASES : GENERAL_TITLE_ALIASES;
        if (aliases.stream().anyMatch(compact::contains)) {
            return true;
        }
        String marker = type == CurriculumType.MAJOR ? "전공" : "교양";
        return compact.contains(marker)
                && COURSE_TERMS.stream().anyMatch(compact::contains)
                && DOCUMENT_TERMS.stream().anyMatch(compact::contains);
    }

    private static Set<CurriculumType> explicitNameTypes(String value) {
        Set<CurriculumType> types = new LinkedHashSet<>();
        String compact = ExcelText.compact(value);
        String lower = value.toLowerCase();
        if (compact.contains("전공") || lower.matches(".*(?<![a-z])major(?![a-z]).*")) {
            types.add(CurriculumType.MAJOR);
        }
        if (compact.contains("교양") || lower.matches(".*(?<![a-z])general(?:[\\s._-]*education)?(?![a-z]).*")) {
            types.add(CurriculumType.GENERAL_EDUCATION);
        }
        return types;
    }

    private static boolean looksLikeAcademicUnit(String value) {
        String compact = ExcelText.compact(value);
        if (compact.isEmpty() || NON_UNIT_GROUP.matcher(compact).matches()
                || UNIT_EXCLUSIONS.stream().anyMatch(compact::contains)) {
            return false;
        }
        return UNIT_SUFFIXES.stream().anyMatch(compact::endsWith);
    }

    private static Map<String, Object> item(
            SheetView view,
            int row,
            String kind,
            String value
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sheetName", view.name());
        item.put("row", row);
        item.put("kind", kind);
        if (value != null && !value.isEmpty()) item.put("value", clipped(value));
        return item;
    }

    private static String clipped(String value) {
        return value.substring(0, Math.min(200, value.length()));
    }

    private static final class Evidence {
        private final Map<CurriculumType, List<Map<String, Object>>> strong = enumLists();
        private final Map<CurriculumType, Integer> supportScores = new EnumMap<>(CurriculumType.class);
        private final Map<CurriculumType, List<Map<String, Object>>> support = enumLists();
        private int genericHeaderCount;

        private Evidence() {
            for (CurriculumType type : CurriculumType.values()) {
                supportScores.put(type, 0);
            }
        }

        private Map<String, Object> details() {
            Map<String, Object> details = new LinkedHashMap<>();
            Map<String, Integer> scores = new LinkedHashMap<>();
            for (CurriculumType type : CurriculumType.values()) {
                scores.put(type.name(), strong.get(type).size() * 100 + supportScores.get(type));
            }
            details.put("scores", scores);
            details.put("genericHeaderCount", genericHeaderCount);
            Map<String, Object> strongDetails = new LinkedHashMap<>();
            Map<String, Object> supportDetails = new LinkedHashMap<>();
            for (CurriculumType type : CurriculumType.values()) {
                if (!strong.get(type).isEmpty()) strongDetails.put(type.name(), strong.get(type));
                if (!support.get(type).isEmpty()) supportDetails.put(type.name(), support.get(type));
            }
            details.put("strongEvidence", strongDetails);
            details.put("supportingEvidence", supportDetails);
            return details;
        }

        private static Map<CurriculumType, List<Map<String, Object>>> enumLists() {
            Map<CurriculumType, List<Map<String, Object>>> values = new EnumMap<>(CurriculumType.class);
            for (CurriculumType type : CurriculumType.values()) values.put(type, new ArrayList<>());
            return values;
        }
    }
}
