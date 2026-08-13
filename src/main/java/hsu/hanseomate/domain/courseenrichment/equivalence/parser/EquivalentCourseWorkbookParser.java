package hsu.hanseomate.domain.courseenrichment.equivalence.parser;

import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseGroupData;
import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseMemberData;
import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseParseResult;
import hsu.hanseomate.domain.courseenrichment.equivalence.support.EquivalentCourseHashing;
import hsu.hanseomate.domain.courseimport.dto.CourseImportIssueResponse;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookLoader;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseException;
import hsu.hanseomate.domain.courseimport.parser.common.ExcelText;
import hsu.hanseomate.domain.courseimport.parser.common.SemesterDetector;
import hsu.hanseomate.domain.courseimport.parser.common.SemesterInfo;
import hsu.hanseomate.domain.courseimport.parser.common.SheetView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EquivalentCourseWorkbookParser {

    static final String SCHEMA_VERSION = "1.0";
    static final String PARSER_VERSION = "equivalent-course-v1";
    static final int DEFAULT_MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    static final int DEFAULT_MAX_SHEETS = 20;
    static final long DEFAULT_MAX_WORKBOOK_CELLS = 500_000L;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".xlsx", ".xlsm");
    private static final Pattern COURSE_CODE = Pattern.compile("\\d{7}");
    private static final Pattern SOURCE_SERIAL = Pattern.compile("\\d+");

    private final CourseWorkbookLoader workbookLoader;
    private final long maxUploadBytes;
    private final int maxSheets;
    private final long maxWorkbookCells;

    public EquivalentCourseWorkbookParser() {
        this(
                new CourseWorkbookLoader(),
                DEFAULT_MAX_UPLOAD_BYTES,
                DEFAULT_MAX_SHEETS,
                DEFAULT_MAX_WORKBOOK_CELLS
        );
    }

    @Autowired
    public EquivalentCourseWorkbookParser(
            @Value("${course-import.max-upload-bytes:10485760}") long maxUploadBytes
    ) {
        this(
                new CourseWorkbookLoader(),
                maxUploadBytes,
                DEFAULT_MAX_SHEETS,
                DEFAULT_MAX_WORKBOOK_CELLS
        );
    }

    EquivalentCourseWorkbookParser(
            CourseWorkbookLoader workbookLoader,
            long maxUploadBytes,
            int maxSheets,
            long maxWorkbookCells
    ) {
        this.workbookLoader = workbookLoader;
        this.maxUploadBytes = maxUploadBytes;
        this.maxSheets = maxSheets;
        this.maxWorkbookCells = maxWorkbookCells;
    }

    public EquivalentCourseParseResult parse(byte[] fileBytes, String fileName) {
        String safeFileName = safeFileName(fileName);
        validateUpload(fileBytes, safeFileName);
        String rawFileSha256 = EquivalentCourseHashing.rawFileSha256(fileBytes);

        try (Workbook workbook = workbookLoader.load(fileBytes)) {
            List<SheetView> views = loadViews(workbook);
            SemesterInfo semester = SemesterDetector.detect(views, safeFileName);
            List<CourseImportIssueResponse> issues = new ArrayList<>();
            List<HeaderLocation> headers = findHeaders(views);
            List<EquivalentCourseGroupData> groups;
            if (headers.isEmpty()) {
                issues.add(error(
                        "EQUIVALENT_COURSE_HEADER_NOT_FOUND",
                        "일련번호, 과목코드, 과목명 헤더를 찾을 수 없습니다.",
                        null,
                        null,
                        "header",
                        null
                ));
                groups = List.of();
            } else if (headers.size() > 1) {
                issues.add(error(
                        "MULTIPLE_EQUIVALENT_COURSE_TABLES",
                        "동일교과목 표가 여러 개 발견되어 자동으로 선택할 수 없습니다.",
                        headers.get(1).view().name(),
                        headers.get(1).rowNumber(),
                        "header",
                        Integer.toString(headers.size())
                ));
                groups = List.of();
            } else {
                groups = parseGroups(headers.get(0), issues);
            }
            if (groups.isEmpty() && issues.stream().noneMatch(issue ->
                    issue.severity() == IssueSeverity.ERROR)) {
                issues.add(error(
                        "NO_EQUIVALENT_COURSES_PARSED",
                        "동일교과목 데이터를 찾을 수 없습니다.",
                        headers.isEmpty() ? null : headers.get(0).view().name(),
                        headers.isEmpty() ? null : headers.get(0).rowNumber(),
                        "groups",
                        null
                ));
            }

            return new EquivalentCourseParseResult(
                    SCHEMA_VERSION,
                    PARSER_VERSION,
                    UUID.randomUUID().toString(),
                    safeFileName,
                    rawFileSha256,
                    EquivalentCourseHashing.canonicalHash(groups),
                    semester.academicYear(),
                    semester.semester(),
                    groups,
                    issues
            );
        } catch (CourseWorkbookParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CourseWorkbookParseException(
                    "WORKBOOK_OPEN_FAILED",
                    "동일교과목 엑셀 파일을 처리할 수 없습니다.",
                    Map.of("reason", exception.getClass().getSimpleName()),
                    exception
            );
        }
    }

    private List<SheetView> loadViews(Workbook workbook) {
        if (workbook.getNumberOfSheets() > maxSheets) {
            throw new CourseWorkbookParseException(
                    "TOO_MANY_SHEETS",
                    "시트 수가 허용 범위를 초과했습니다.",
                    Map.of("actual", workbook.getNumberOfSheets(), "max", maxSheets)
            );
        }
        List<SheetView> views = new ArrayList<>();
        long totalCells = 0;
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            SheetView view = new SheetView(workbook.getSheetAt(index));
            totalCells += (long) view.maxRow() * view.maxColumn();
            if (totalCells > maxWorkbookCells) {
                throw new CourseWorkbookParseException(
                        "WORKBOOK_TOO_LARGE",
                        "워크북 사용 범위가 허용 범위를 초과했습니다.",
                        Map.of("actualCells", totalCells, "maxCells", maxWorkbookCells)
                );
            }
            views.add(view);
        }
        return List.copyOf(views);
    }

    private List<HeaderLocation> findHeaders(List<SheetView> views) {
        List<HeaderLocation> headers = new ArrayList<>();
        for (SheetView view : views) {
            for (int row = 1; row <= view.maxRow(); row++) {
                String serialHeader = ExcelText.normalize(view.text(row, 1, false, false));
                String codeHeader = ExcelText.normalize(view.text(row, 3, false, false));
                String nameHeader = ExcelText.normalize(view.text(row, 4, false, false));
                if ("일련번호".equals(serialHeader)
                        && "과목코드".equals(codeHeader)
                        && "과목명".equals(nameHeader)) {
                    headers.add(new HeaderLocation(view, row));
                }
            }
        }
        return headers;
    }

    private List<EquivalentCourseGroupData> parseGroups(
            HeaderLocation header,
            List<CourseImportIssueResponse> issues
    ) {
        SheetView view = header.view();
        List<EquivalentCourseGroupData> groups = new ArrayList<>();
        Set<Integer> closedSerials = new HashSet<>();
        Map<String, EquivalentCourseMemberData> memberByCode = new HashMap<>();
        MutableGroup current = null;

        for (int row = header.rowNumber() + 1; row <= view.maxRow(); row++) {
            String serialText = ExcelText.normalize(view.text(row, 1, true, false));
            String code = ExcelText.normalize(view.text(row, 3, true, false));
            String name = ExcelText.normalize(view.text(row, 4, false, false));
            if (code.isEmpty() && name.isEmpty()) {
                continue;
            }

            if (!serialText.isEmpty()) {
                Integer serial = parseSerial(serialText, view.name(), row, issues);
                if (serial == null) {
                    if (current != null) {
                        groups.add(current.toData());
                        closedSerials.add(current.sourceSerial);
                    }
                    current = new MutableGroup(-row, groups.size(), view.name(), row);
                } else if (current == null) {
                    current = new MutableGroup(serial, groups.size(), view.name(), row);
                } else if (serial != current.sourceSerial) {
                    groups.add(current.toData());
                    closedSerials.add(current.sourceSerial);
                    if (closedSerials.contains(serial)) {
                        issues.add(error(
                                "NON_CONTIGUOUS_SERIAL_REAPPEARED",
                                "종료된 일련번호가 비연속 위치에서 다시 등장했습니다.",
                                view.name(),
                                row,
                                "sourceSerial",
                                serialText
                        ));
                    }
                    current = new MutableGroup(serial, groups.size(), view.name(), row);
                }
            } else if (current == null) {
                issues.add(error(
                        "MISSING_INITIAL_SERIAL",
                        "첫 동일교과목 행에 일련번호가 없습니다.",
                        view.name(),
                        row,
                        "sourceSerial",
                        null
                ));
                current = new MutableGroup(-row, groups.size(), view.name(), row);
            }

            validateMember(code, name, view.name(), row, issues);
            EquivalentCourseMemberData member = new EquivalentCourseMemberData(
                    code,
                    name,
                    view.name(),
                    row,
                    current.members.size()
            );
            EquivalentCourseMemberData duplicate = memberByCode.putIfAbsent(code, member);
            if (!code.isEmpty() && duplicate != null) {
                issues.add(error(
                        "DUPLICATE_EQUIVALENT_COURSE_CODE",
                        "같은 과목코드가 동일교과목 파일에 두 번 이상 등장했습니다.",
                        view.name(),
                        row,
                        "courseCode",
                        code
                ));
            }
            current.members.add(member);
            current.sourceEndRow = row;
        }
        if (current != null) {
            groups.add(current.toData());
        }

        for (EquivalentCourseGroupData group : groups) {
            if (group.members().size() == 1) {
                issues.add(warning(
                        "SINGLETON_EQUIVALENT_COURSE_GROUP",
                        "동일교과목 그룹에 과목이 한 개만 있습니다.",
                        group.sourceSheet(),
                        group.sourceStartRow(),
                        "sourceSerial",
                        Integer.toString(group.sourceSerial())
                ));
            }
        }
        return List.copyOf(groups);
    }

    private Integer parseSerial(
            String serialText,
            String sheetName,
            int row,
            List<CourseImportIssueResponse> issues
    ) {
        if (!SOURCE_SERIAL.matcher(serialText).matches()) {
            issues.add(error(
                    "INVALID_EQUIVALENT_COURSE_SERIAL",
                    "일련번호는 0 이상의 정수여야 합니다.",
                    sheetName,
                    row,
                    "sourceSerial",
                    serialText
            ));
            return null;
        }
        try {
            return Integer.valueOf(serialText);
        } catch (NumberFormatException exception) {
            issues.add(error(
                    "INVALID_EQUIVALENT_COURSE_SERIAL",
                    "일련번호가 저장 가능한 정수 범위를 초과했습니다.",
                    sheetName,
                    row,
                    "sourceSerial",
                    serialText
            ));
            return null;
        }
    }

    private void validateMember(
            String code,
            String name,
            String sheetName,
            int row,
            List<CourseImportIssueResponse> issues
    ) {
        if (!COURSE_CODE.matcher(code).matches()) {
            issues.add(error(
                    "INVALID_EQUIVALENT_COURSE_CODE",
                    "과목코드는 선행 0을 포함한 7자리 숫자여야 합니다.",
                    sheetName,
                    row,
                    "courseCode",
                    code
            ));
        }
        if (name.isEmpty()) {
            issues.add(error(
                    "MISSING_EQUIVALENT_COURSE_NAME",
                    "과목명이 비어 있습니다.",
                    sheetName,
                    row,
                    "courseName",
                    null
            ));
        } else if (name.length() > 255) {
            issues.add(error(
                    "EQUIVALENT_COURSE_NAME_TOO_LONG",
                    "과목명은 255자 이하여야 합니다.",
                    sheetName,
                    row,
                    "courseName",
                    name
            ));
        }
    }

    private void validateUpload(byte[] fileBytes, String fileName) {
        if (fileName.isEmpty()) {
            throw new CourseWorkbookParseException(
                    "FILE_NAME_MISSING",
                    "파일 이름이 없습니다."
            );
        }
        String extension = extensionOf(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new CourseWorkbookParseException(
                    "UNSUPPORTED_EXTENSION",
                    "현재는 .xlsx와 .xlsm 파일만 지원합니다.",
                    Map.of("extension", extension)
            );
        }
        if (fileBytes == null || fileBytes.length == 0) {
            throw new CourseWorkbookParseException("EMPTY_FILE", "빈 파일은 업로드할 수 없습니다.");
        }
        if (fileBytes.length > maxUploadBytes) {
            throw new CourseWorkbookParseException(
                    "FILE_TOO_LARGE",
                    "업로드 파일이 허용 크기를 초과했습니다.",
                    Map.of("actualBytes", fileBytes.length, "maxBytes", maxUploadBytes)
            );
        }
        if (fileBytes.length < 2 || fileBytes[0] != 'P' || fileBytes[1] != 'K') {
            throw new CourseWorkbookParseException(
                    "INVALID_XLSX_SIGNATURE",
                    "유효한 Office Open XML 파일이 아닙니다."
            );
        }
    }

    private String safeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String normalized = fileName.replace('\\', '/');
        String safeName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (safeName.length() <= 500) {
            return safeName;
        }
        String extension = extensionOf(safeName);
        return safeName.substring(0, 500 - extension.length()) + extension;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private CourseImportIssueResponse error(
            String code,
            String message,
            String sheetName,
            Integer rowNumber,
            String field,
            String rawValue
    ) {
        return new CourseImportIssueResponse(
                IssueSeverity.ERROR,
                code,
                message,
                sheetName,
                rowNumber,
                field,
                rawValue
        );
    }

    private CourseImportIssueResponse warning(
            String code,
            String message,
            String sheetName,
            Integer rowNumber,
            String field,
            String rawValue
    ) {
        return new CourseImportIssueResponse(
                IssueSeverity.WARNING,
                code,
                message,
                sheetName,
                rowNumber,
                field,
                rawValue
        );
    }

    private record HeaderLocation(SheetView view, int rowNumber) {
    }

    private static final class MutableGroup {

        private final int sourceSerial;
        private final int groupOrder;
        private final String sourceSheet;
        private final int sourceStartRow;
        private int sourceEndRow;
        private final List<EquivalentCourseMemberData> members = new ArrayList<>();

        private MutableGroup(
                int sourceSerial,
                int groupOrder,
                String sourceSheet,
                int sourceStartRow
        ) {
            this.sourceSerial = sourceSerial;
            this.groupOrder = groupOrder;
            this.sourceSheet = sourceSheet;
            this.sourceStartRow = sourceStartRow;
            this.sourceEndRow = sourceStartRow;
        }

        private EquivalentCourseGroupData toData() {
            return new EquivalentCourseGroupData(
                    sourceSerial,
                    groupOrder,
                    sourceSheet,
                    sourceStartRow,
                    sourceEndRow,
                    members
            );
        }
    }
}
