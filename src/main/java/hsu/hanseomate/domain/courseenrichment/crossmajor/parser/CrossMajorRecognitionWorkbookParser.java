package hsu.hanseomate.domain.courseenrichment.crossmajor.parser;

import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionIssueResponse;
import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionParseResult;
import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionRuleData;
import hsu.hanseomate.domain.courseenrichment.crossmajor.support.CrossMajorRecognitionNormalizer;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookLoader;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseException;
import hsu.hanseomate.domain.courseimport.parser.common.SheetView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class CrossMajorRecognitionWorkbookParser {

    private static final List<String> HEADERS = List.of(
            "연번", "학생학부", "학생학과", "학생전공", "개설학부", "개설학과",
            "개설전공", "과목코드", "과목명", "적용년도", "적용학기"
    );
    private static final List<Pattern> SCOPE_PATTERNS = List.of(
            Pattern.compile("(?<!\\d)(?<year>20\\d{2})\\s*(?:학년도|년도|년)?\\s*"
                    + "(?:[-._/]?\\s*)?(?:제\\s*)?(?<semester>[12])\\s*학기"),
            Pattern.compile("(?<!\\d)(?<year>20\\d{2})\\s*[-._/]\\s*"
                    + "(?<semester>[12])(?!\\s*[-._/]\\s*\\d)"),
            Pattern.compile("(?<!\\d)(?:제\\s*)?(?<semester>[12])\\s*학기\\s*"
                    + "[-._/]?\\s*(?<year>20\\d{2})\\s*(?:학년도|년도|년)?(?!\\d)")
    );

    private final CourseWorkbookLoader workbookLoader;

    public CrossMajorRecognitionWorkbookParser() {
        this(new CourseWorkbookLoader());
    }

    CrossMajorRecognitionWorkbookParser(CourseWorkbookLoader workbookLoader) {
        this.workbookLoader = workbookLoader;
    }

    public CrossMajorRecognitionParseResult parse(byte[] fileBytes, String fileName) {
        String safeName = safeFileName(fileName);
        try (Workbook workbook = workbookLoader.load(fileBytes)) {
            Scope scope = detectScope(workbook, safeName);
            SheetView source = findSourceSheet(workbook);
            return parseSource(fileBytes, safeName, scope, source);
        } catch (CourseWorkbookParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CourseWorkbookParseException(
                    "WORKBOOK_OPEN_FAILED",
                    "타학과 전공인정 엑셀 파일을 처리할 수 없습니다.",
                    Map.of("reason", exception.getClass().getSimpleName()),
                    exception
            );
        }
    }

    private CrossMajorRecognitionParseResult parseSource(
            byte[] fileBytes,
            String fileName,
            Scope scope,
            SheetView source
    ) {
        List<CrossMajorRecognitionIssueResponse> issues = new ArrayList<>();
        LinkedHashMap<String, CrossMajorRecognitionRuleData> uniqueRules = new LinkedHashMap<>();
        int rawRowCount = 0;
        for (int row = 2; row <= source.maxRow(); row++) {
            List<String> values = readDataValues(source, row);
            if (values.stream().allMatch(String::isEmpty)) {
                continue;
            }
            rawRowCount++;
            int issueStart = issues.size();
            validateRequiredValues(values, source.name(), row, issues);

            String courseCode = CrossMajorRecognitionNormalizer.courseCode(values.get(6));
            if (!values.get(6).isEmpty() && courseCode == null) {
                issues.add(CrossMajorRecognitionIssueResponse.error(
                        "INVALID_COURSE_CODE",
                        "과목코드는 1~7자리 숫자여야 합니다.",
                        source.name(), row, "courseCode", values.get(6)
                ));
            }
            Integer effectiveYear = parseYear(values.get(8));
            if (!values.get(8).isEmpty() && effectiveYear == null) {
                issues.add(CrossMajorRecognitionIssueResponse.error(
                        "INVALID_EFFECTIVE_YEAR",
                        "적용년도는 2000~2100 범위의 연도여야 합니다.",
                        source.name(), row, "effectiveYear", values.get(8)
                ));
            } else if (effectiveYear != null && effectiveYear > scope.policyYear()) {
                issues.add(CrossMajorRecognitionIssueResponse.error(
                        "EFFECTIVE_YEAR_AFTER_POLICY_YEAR",
                        "적용년도가 정책 연도보다 늦습니다.",
                        source.name(), row, "effectiveYear", values.get(8)
                ));
            }
            Integer effectiveSemester = parseSemester(values.get(9));
            if (!values.get(9).isEmpty() && effectiveSemester == null) {
                issues.add(CrossMajorRecognitionIssueResponse.error(
                        "INVALID_EFFECTIVE_SEMESTER",
                        "적용학기는 1학기 또는 2학기여야 합니다.",
                        source.name(), row, "effectiveSemester", values.get(9)
                ));
            }
            if (hasNewError(issues, issueStart)) {
                continue;
            }

            CrossMajorRecognitionRuleData rule = createRule(
                    values,
                    courseCode,
                    effectiveYear,
                    effectiveSemester,
                    source.name(),
                    row
            );
            CrossMajorRecognitionRuleData duplicate = uniqueRules.putIfAbsent(
                    rule.canonicalLine(), rule
            );
            if (duplicate != null) {
                issues.add(CrossMajorRecognitionIssueResponse.warning(
                        "DUPLICATE_RULE",
                        "동일한 전공인정 규칙이 중복되어 첫 번째 행만 사용합니다.",
                        source.name(), row, null,
                        "firstRow=" + duplicate.sourceRow()
                ));
            }
        }

        List<CrossMajorRecognitionRuleData> rules = List.copyOf(uniqueRules.values());
        addAmbiguousIdentityWarnings(rules, source.name(), issues);
        String canonicalHash = canonicalHash(rules);
        return new CrossMajorRecognitionParseResult(
                fileName,
                sha256(fileBytes),
                canonicalHash,
                scope.policyYear(),
                scope.uploadedSemester(),
                source.name(),
                rawRowCount,
                rules,
                issues
        );
    }

    private Scope detectScope(Workbook workbook, String fileName) {
        List<ScopeSource> sources = new ArrayList<>();
        sources.add(new ScopeSource("FILE_NAME", fileName));
        if (workbook instanceof XSSFWorkbook xssfWorkbook) {
            String title = xssfWorkbook.getProperties().getCoreProperties().getTitle();
            if (title != null && !title.isBlank()) {
                sources.add(new ScopeSource("DOCUMENT_TITLE", title));
            }
        }
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            sources.add(new ScopeSource("SHEET_NAME", workbook.getSheetName(index)));
        }

        LinkedHashMap<Scope, List<ScopeSource>> detected = new LinkedHashMap<>();
        for (ScopeSource source : sources) {
            for (Scope scope : scopesIn(source.value())) {
                detected.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(source);
            }
        }
        if (detected.isEmpty()) {
            throw new CourseWorkbookParseException(
                    "SEMESTER_NOT_FOUND",
                    "파일명, 문서 제목 또는 시트명에서 정책 연도와 업로드 학기를 찾을 수 없습니다."
            );
        }
        if (detected.size() > 1) {
            throw new CourseWorkbookParseException(
                    "SEMESTER_CONFLICT",
                    "파일명, 문서 제목 또는 시트명에서 서로 다른 정책 범위가 발견되었습니다.",
                    Map.of("scopes", detected.keySet().stream()
                            .sorted(Comparator.comparingInt(Scope::policyYear)
                                    .thenComparingInt(Scope::uploadedSemester))
                            .map(scope -> Map.of(
                                    "policyYear", scope.policyYear(),
                                    "uploadedSemester", scope.uploadedSemester()
                            ))
                            .toList())
            );
        }
        return detected.keySet().iterator().next();
    }

    private Set<Scope> scopesIn(String value) {
        String normalized = CrossMajorRecognitionNormalizer.text(value);
        Set<Scope> result = new LinkedHashSet<>();
        for (Pattern pattern : SCOPE_PATTERNS) {
            Matcher matcher = pattern.matcher(normalized);
            while (matcher.find()) {
                result.add(new Scope(
                        Integer.parseInt(matcher.group("year")),
                        Integer.parseInt(matcher.group("semester"))
                ));
            }
        }
        return result;
    }

    private SheetView findSourceSheet(Workbook workbook) {
        List<SheetView> matches = new ArrayList<>();
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            SheetView view = new SheetView(workbook.getSheetAt(index));
            if (hasExpectedHeaders(view)) {
                matches.add(view);
            }
        }
        if (matches.isEmpty()) {
            throw new CourseWorkbookParseException(
                    "SOURCE_SHEET_NOT_FOUND",
                    "타학과 전공인정 교과목 목록 시트를 찾을 수 없습니다.",
                    Map.of("requiredHeaders", HEADERS)
            );
        }
        if (matches.size() > 1) {
            throw new CourseWorkbookParseException(
                    "SOURCE_SHEET_CONFLICT",
                    "동일한 타학과 전공인정 헤더를 가진 시트가 여러 개입니다.",
                    Map.of("sheetNames", matches.stream().map(SheetView::name).toList())
            );
        }
        return matches.get(0);
    }

    private boolean hasExpectedHeaders(SheetView view) {
        if (view.maxColumn() < HEADERS.size()) {
            return false;
        }
        for (int index = 0; index < HEADERS.size(); index++) {
            if (!HEADERS.get(index).equals(view.text(1, index + 1))) {
                return false;
            }
        }
        return true;
    }

    private List<String> readDataValues(SheetView source, int row) {
        List<String> values = new ArrayList<>(10);
        for (int column = 2; column <= 11; column++) {
            boolean identifier = column == 8;
            values.add(CrossMajorRecognitionNormalizer.text(
                    source.text(row, column, identifier)
            ));
        }
        return values;
    }

    private void validateRequiredValues(
            List<String> values,
            String sheetName,
            int row,
            List<CrossMajorRecognitionIssueResponse> issues
    ) {
        String[] fields = {
                "studentCollegeName", "studentDepartmentName", "studentMajorName",
                "offeringCollegeName", "offeringDepartmentName", "offeringMajorName",
                "courseCode", "courseName", "effectiveYear", "effectiveSemester"
        };
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (value.isEmpty()) {
                issues.add(CrossMajorRecognitionIssueResponse.error(
                        "MISSING_REQUIRED_VALUE",
                        "필수 값이 비어 있습니다.",
                        sheetName, row, fields[index], null
                ));
            } else if (index < 8 && index != 6 && value.length() > 255) {
                issues.add(CrossMajorRecognitionIssueResponse.error(
                        "VALUE_TOO_LONG",
                        "문자열 값은 255자를 초과할 수 없습니다.",
                        sheetName, row, fields[index], value
                ));
            }
        }
    }

    private CrossMajorRecognitionRuleData createRule(
            List<String> values,
            String courseCode,
            Integer effectiveYear,
            Integer effectiveSemester,
            String sourceSheet,
            int sourceRow
    ) {
        String canonicalLine = String.join("\u001f",
                values.get(0), values.get(1), values.get(2), values.get(3), values.get(4),
                values.get(5), courseCode, values.get(7),
                Integer.toString(effectiveYear), Integer.toString(effectiveSemester)
        );
        return new CrossMajorRecognitionRuleData(
                sha256(canonicalLine.getBytes(StandardCharsets.UTF_8)),
                values.get(0), values.get(1), values.get(2),
                values.get(3), values.get(4), values.get(5),
                CrossMajorRecognitionNormalizer.key(values.get(4)),
                CrossMajorRecognitionNormalizer.key(values.get(5)),
                courseCode,
                values.get(7),
                CrossMajorRecognitionNormalizer.key(values.get(7)),
                effectiveYear,
                effectiveSemester,
                sourceSheet,
                sourceRow
        );
    }

    private Integer parseYear(String value) {
        if (!value.matches("\\d{4}")) {
            return null;
        }
        int year = Integer.parseInt(value);
        return year >= 2000 && year <= 2100 ? year : null;
    }

    private Integer parseSemester(String value) {
        String compact = value.replace(" ", "");
        if (compact.matches("1(?:학기)?")) return 1;
        if (compact.matches("2(?:학기)?")) return 2;
        return null;
    }

    private boolean hasNewError(
            List<CrossMajorRecognitionIssueResponse> issues,
            int issueStart
    ) {
        return issues.subList(issueStart, issues.size()).stream()
                .anyMatch(issue -> issue.severity()
                        == hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity.ERROR);
    }

    private void addAmbiguousIdentityWarnings(
            List<CrossMajorRecognitionRuleData> rules,
            String sheetName,
            List<CrossMajorRecognitionIssueResponse> issues
    ) {
        Map<String, Set<String>> namesByIdentity = new LinkedHashMap<>();
        Map<String, Integer> firstRows = new LinkedHashMap<>();
        for (CrossMajorRecognitionRuleData rule : rules) {
            String identity = String.join("|",
                    rule.courseCode(), rule.offeringDepartmentKey(), rule.offeringMajorKey()
            );
            namesByIdentity.computeIfAbsent(identity, ignored -> new LinkedHashSet<>())
                    .add(rule.courseNameKey());
            firstRows.putIfAbsent(identity, rule.sourceRow());
        }
        namesByIdentity.forEach((identity, names) -> {
            if (names.size() > 1) {
                issues.add(CrossMajorRecognitionIssueResponse.warning(
                        "AMBIGUOUS_COURSE_IDENTITY",
                        "같은 과목코드와 개설 조직에 서로 다른 과목명이 있어 조회 시 과목명으로 구분합니다.",
                        sheetName, firstRows.get(identity), "courseName", identity
                ));
            }
        });
    }

    private String canonicalHash(List<CrossMajorRecognitionRuleData> rules) {
        String payload = rules.stream()
                .map(CrossMajorRecognitionRuleData::canonicalLine)
                .sorted()
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return sha256(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String safeFileName(String fileName) {
        if (fileName == null) return "";
        String normalized = fileName.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String safe = normalized.substring(separator + 1).trim();
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private record Scope(int policyYear, int uploadedSemester) {
    }

    private record ScopeSource(String type, String value) {
    }
}
