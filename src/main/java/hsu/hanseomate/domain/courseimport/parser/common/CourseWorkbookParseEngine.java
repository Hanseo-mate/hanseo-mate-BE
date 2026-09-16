package hsu.hanseomate.domain.courseimport.parser.common;

import hsu.hanseomate.domain.courseimport.dto.ParseStatisticsRequest;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import hsu.hanseomate.domain.courseimport.dto.type.ParseStatus;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Validates and loads one uploaded workbook, delegates row parsing, and builds
 * the stable timetable-import contract consumed by the Spring storage layer.
 */
public final class CourseWorkbookParseEngine {

    public static final int DEFAULT_MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    public static final int DEFAULT_MAX_SHEETS = 20;
    public static final long DEFAULT_MAX_WORKBOOK_CELLS = 500_000L;

    private static final String SCHEMA_VERSION = "1.2";
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".xlsx", ".xlsm");

    private final CourseWorkbookLoader workbookLoader;
    private final int maxUploadBytes;
    private final int maxSheets;
    private final long maxWorkbookCells;

    public CourseWorkbookParseEngine() {
        this(
                new CourseWorkbookLoader(),
                DEFAULT_MAX_UPLOAD_BYTES,
                DEFAULT_MAX_SHEETS,
                DEFAULT_MAX_WORKBOOK_CELLS
        );
    }

    CourseWorkbookParseEngine(
            CourseWorkbookLoader workbookLoader,
            int maxUploadBytes,
            int maxSheets,
            long maxWorkbookCells
    ) {
        this.workbookLoader = workbookLoader;
        this.maxUploadBytes = maxUploadBytes;
        this.maxSheets = maxSheets;
        this.maxWorkbookCells = maxWorkbookCells;
    }

    public TimetableParseResultRequest parse(
            byte[] fileBytes,
            String fileName,
            CourseSheetParser sheetParser
    ) {
        if (sheetParser == null) {
            throw new IllegalArgumentException("sheetParser must not be null");
        }
        String safeName = safeFileName(fileName);
        validateUpload(fileBytes, safeName);

        try (Workbook workbook = workbookLoader.load(fileBytes)) {
            List<SheetView> views = loadViews(workbook);
            SemesterInfo semester = SemesterDetector.detect(views, safeName);
            CurriculumType curriculumType = CurriculumTypeDetector.detect(
                    views,
                    sheetParser.curriculumType(),
                    safeName
            );
            CourseParserOutput output = sheetParser.parse(List.copyOf(views));
            boolean hasDetailedError = output.issues().stream()
                    .anyMatch(issue -> issue.severity() == IssueSeverity.ERROR);
            if (output.lectures().isEmpty() && !hasDetailedError) {
                throw new CourseWorkbookParseException(
                        "NO_LECTURES_PARSED",
                        "필수 헤더는 찾았지만 파싱된 강좌가 없습니다.",
                        Map.of("headerCount", output.headerCount())
                );
            }

            ParseStatisticsRequest statistics = statistics(views, output);
            ParseStatus status = statistics.errorCount() > 0
                    ? ParseStatus.REVIEW_REQUIRED
                    : ParseStatus.READY;

            return new TimetableParseResultRequest(
                    SCHEMA_VERSION,
                    UUID.randomUUID().toString(),
                    safeName,
                    sha256(fileBytes),
                    curriculumType,
                    output.parserVersion(),
                    semester.academicYear(),
                    semester.semester(),
                    semester.displayName(),
                    status,
                    confidence(output, statistics),
                    statistics,
                    List.copyOf(output.academicUnits()),
                    List.copyOf(output.generalCategories()),
                    List.copyOf(output.generalCategoryNodes()),
                    List.copyOf(output.lectures()),
                    List.copyOf(output.issues())
            );
        } catch (CourseWorkbookParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CourseWorkbookParseException(
                    "WORKBOOK_OPEN_FAILED",
                    "엑셀 파일을 처리할 수 없습니다.",
                    Map.of("reason", exception.getClass().getSimpleName()),
                    exception
            );
        }
    }

    private List<SheetView> loadViews(Workbook workbook) {
        int sheetCount = workbook.getNumberOfSheets();
        if (sheetCount > maxSheets) {
            throw new CourseWorkbookParseException(
                    "TOO_MANY_SHEETS",
                    "시트 수가 허용 범위를 초과했습니다.",
                    Map.of("actual", sheetCount, "max", maxSheets)
            );
        }
        List<SheetView> views = new ArrayList<>(sheetCount);
        long totalCells = 0;
        for (int index = 0; index < sheetCount; index++) {
            SheetView view = new SheetView(workbook.getSheetAt(index));
            totalCells += (long) view.maxRow() * view.maxColumn();
            if (totalCells > maxWorkbookCells) {
                throw new CourseWorkbookParseException(
                        "WORKBOOK_TOO_LARGE",
                        "워크북 셀 범위가 허용 범위를 초과했습니다.",
                        Map.of("actualCells", totalCells, "maxCells", maxWorkbookCells)
                );
            }
            views.add(view);
        }
        return views;
    }

    private void validateUpload(byte[] fileBytes, String safeName) {
        if (safeName.isEmpty()) {
            throw new CourseWorkbookParseException("FILE_NAME_MISSING", "파일 이름이 없습니다.");
        }
        String extension = extensionOf(safeName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new CourseWorkbookParseException(
                    "UNSUPPORTED_EXTENSION",
                    "현재는 .xlsx와 .xlsm 파일만 지원합니다.",
                    Map.of("extension", extension.isEmpty() ? nullValue() : extension)
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
                    "유효한 Office Open XML 엑셀 파일이 아닙니다."
            );
        }
    }

    private static ParseStatisticsRequest statistics(
            List<SheetView> views,
            CourseParserOutput output
    ) {
        int warningCount = (int) output.issues().stream()
                .filter(issue -> issue.severity() == IssueSeverity.WARNING)
                .count();
        int errorCount = (int) output.issues().stream()
                .filter(issue -> issue.severity() == IssueSeverity.ERROR)
                .count();
        int scheduleCount = output.lectures().stream()
                .mapToInt(lecture -> lecture.schedules().size())
                .sum();
        int periodCount = output.lectures().stream()
                .flatMap(lecture -> lecture.schedules().stream())
                .mapToInt(schedule -> schedule.periods().size())
                .sum();
        int missingScheduleCount = (int) output.lectures().stream()
                .filter(lecture -> lecture.schedules().isEmpty())
                .count();
        int missingClassroomCount = (int) output.lectures().stream()
                .filter(lecture -> lecture.classroomText() == null || lecture.classroomText().isBlank())
                .count();

        return new ParseStatisticsRequest(
                views.size(),
                views.stream().mapToInt(SheetView::maxRow).sum(),
                output.lectures().size(),
                output.failedRowCount(),
                warningCount,
                errorCount,
                output.academicUnits().size(),
                output.generalCategories().size(),
                output.generalCategoryNodes().size(),
                scheduleCount,
                periodCount,
                missingScheduleCount,
                missingClassroomCount
        );
    }

    private static double confidence(
            CourseParserOutput output,
            ParseStatisticsRequest statistics
    ) {
        int totalCandidates = statistics.parsedLectureCount() + statistics.failedRowCount();
        double rowSuccess = (double) statistics.parsedLectureCount() / Math.max(totalCandidates, 1);
        long withContext = output.lectures().stream()
                .filter(lecture -> lecture.academicUnit() != null || lecture.generalEducation() != null)
                .count();
        double contextSuccess = (double) withContext / Math.max(statistics.parsedLectureCount(), 1);
        double errorPenalty = Math.min(
                0.35,
                (double) statistics.errorCount() / Math.max(totalCandidates, 1)
        );
        double score = 0.25 + (0.5 * rowSuccess) + (0.25 * contextSuccess) - errorPenalty;
        double clamped = Math.max(0.0, Math.min(1.0, score));
        return Math.round(clamped * 10_000.0) / 10_000.0;
    }

    private static String safeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String normalized = fileName.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String safeName = normalized.substring(separator + 1).trim();
        if (safeName.length() <= 500) {
            return safeName;
        }
        String extension = extensionOf(safeName);
        int baseLength = Math.max(0, 500 - extension.length());
        return safeName.substring(0, baseLength) + extension;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String nullValue() {
        // Map.of rejects null values; the empty value represents a missing extension.
        return "";
    }
}
