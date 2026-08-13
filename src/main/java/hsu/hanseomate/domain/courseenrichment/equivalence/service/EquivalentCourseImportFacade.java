package hsu.hanseomate.domain.courseenrichment.equivalence.service;

import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseImportResponse;
import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseParseResult;
import hsu.hanseomate.domain.courseenrichment.equivalence.parser.EquivalentCourseWorkbookParser;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseException;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EquivalentCourseImportFacade {

    private final EquivalentCourseWorkbookParser workbookParser;
    private final EquivalentCourseImportService importService;
    private final long maxUploadBytes;

    public EquivalentCourseImportFacade(
            EquivalentCourseWorkbookParser workbookParser,
            EquivalentCourseImportService importService,
            @Value("${course-import.max-upload-bytes:10485760}") long maxUploadBytes
    ) {
        this.workbookParser = workbookParser;
        this.importService = importService;
        this.maxUploadBytes = maxUploadBytes;
    }

    public EquivalentCourseImportResponse importWorkbook(MultipartFile file) {
        if (file == null) {
            throw new CourseWorkbookParseException(
                    "FILE_MISSING",
                    "업로드할 동일교과목 엑셀 파일이 없습니다."
            );
        }
        if (file.getSize() > maxUploadBytes) {
            throw new CourseWorkbookParseException(
                    "FILE_TOO_LARGE",
                    "업로드 파일이 허용 크기를 초과했습니다.",
                    Map.of("actualBytes", file.getSize(), "maxBytes", maxUploadBytes)
            );
        }

        EquivalentCourseParseResult parsed = workbookParser.parse(
                readBytes(file),
                file.getOriginalFilename()
        );
        try {
            return importService.importSnapshot(parsed);
        } catch (DataIntegrityViolationException firstInsertRace) {
            if (!isActiveScopeRace(firstInsertRace)) {
                throw firstInsertRace;
            }
            return importService.importSnapshot(parsed);
        } catch (PessimisticLockingFailureException concurrentScopeUpdate) {
            return importService.importSnapshot(parsed);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new CourseWorkbookParseException(
                    "FILE_READ_FAILED",
                    "업로드한 동일교과목 엑셀 파일을 읽을 수 없습니다.",
                    Map.of("reason", exception.getClass().getSimpleName()),
                    exception
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
                        || normalized.contains("uk_equivalent_active_scope")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
