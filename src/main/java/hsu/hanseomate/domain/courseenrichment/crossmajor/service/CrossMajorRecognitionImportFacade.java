package hsu.hanseomate.domain.courseenrichment.crossmajor.service;

import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionImportResponse;
import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionParseResult;
import hsu.hanseomate.domain.courseenrichment.crossmajor.parser.CrossMajorRecognitionWorkbookParser;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseException;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CrossMajorRecognitionImportFacade {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".xlsx", ".xlsm");

    private final CrossMajorRecognitionWorkbookParser parser;
    private final CrossMajorRecognitionImportService importService;
    private final long maxUploadBytes;

    public CrossMajorRecognitionImportFacade(
            CrossMajorRecognitionWorkbookParser parser,
            CrossMajorRecognitionImportService importService,
            @Value("${course-enrichment.cross-major.max-upload-bytes:10485760}")
            long maxUploadBytes
    ) {
        this.parser = parser;
        this.importService = importService;
        this.maxUploadBytes = maxUploadBytes;
    }

    public CrossMajorRecognitionImportResponse importWorkbook(MultipartFile file) {
        validate(file);
        byte[] bytes = readBytes(file);
        validateContent(bytes);
        CrossMajorRecognitionParseResult parsed = parser.parse(
                bytes,
                file.getOriginalFilename()
        );
        try {
            return importService.importParsed(parsed);
        } catch (DataIntegrityViolationException firstInsertRace) {
            if (!isActiveScopeRace(firstInsertRace)) {
                throw firstInsertRace;
            }
            // Two application instances may both observe an empty annual scope. The unique
            // active-scope key chooses one winner; this fresh transaction then re-evaluates
            // the loser as a duplicate or as the latest replacement.
            return importService.importParsed(parsed);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null) {
            throw new CourseWorkbookParseException(
                    "FILE_MISSING",
                    "업로드할 타학과 전공인정 엑셀 파일이 없습니다."
            );
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new CourseWorkbookParseException("FILE_NAME_MISSING", "파일 이름이 없습니다.");
        }
        String extension = extension(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new CourseWorkbookParseException(
                    "UNSUPPORTED_EXTENSION",
                    "현재는 .xlsx와 .xlsm 파일만 지원합니다.",
                    Map.of("extension", extension)
            );
        }
        if (file.isEmpty()) {
            throw new CourseWorkbookParseException("EMPTY_FILE", "빈 파일은 업로드할 수 없습니다.");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new CourseWorkbookParseException(
                    "FILE_TOO_LARGE",
                    "업로드 파일이 허용 크기를 초과했습니다.",
                    Map.of("actualBytes", file.getSize(), "maxBytes", maxUploadBytes)
            );
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new CourseWorkbookParseException(
                    "FILE_READ_FAILED",
                    "업로드한 파일을 읽을 수 없습니다.",
                    Map.of("reason", exception.getClass().getSimpleName()),
                    exception
            );
        }
    }

    private void validateContent(byte[] bytes) {
        if (bytes.length > maxUploadBytes) {
            throw new CourseWorkbookParseException(
                    "FILE_TOO_LARGE",
                    "업로드 파일이 허용 크기를 초과했습니다.",
                    Map.of("actualBytes", bytes.length, "maxBytes", maxUploadBytes)
            );
        }
        if (bytes.length < 2 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new CourseWorkbookParseException(
                    "INVALID_XLSX_SIGNATURE",
                    "유효한 Office Open XML 엑셀 파일이 아닙니다."
            );
        }
    }

    private boolean isActiveScopeRace(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("active_scope_key")
                        || normalized.contains("activescopekey")
                        || normalized.contains("uk_cross_major_active_scope")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }
}
