package hsu.hanseomate.domain.courseimport.service;

import hsu.hanseomate.domain.courseimport.dto.CourseImportResponse;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseException;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParser;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CourseExcelImportFacade {

    private final CourseImportService courseImportService;
    private final Map<CurriculumType, CourseWorkbookParser> parsers;
    private final long maxUploadBytes;

    public CourseExcelImportFacade(
            CourseImportService courseImportService,
            List<CourseWorkbookParser> parsers,
            @Value("${course-import.max-upload-bytes:10485760}") long maxUploadBytes
    ) {
        this.courseImportService = courseImportService;
        this.maxUploadBytes = maxUploadBytes;
        this.parsers = new EnumMap<>(CurriculumType.class);
        for (CourseWorkbookParser parser : parsers) {
            CourseWorkbookParser duplicate = this.parsers.put(parser.curriculumType(), parser);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "교육과정 유형별 엑셀 파서는 하나만 등록할 수 있습니다: "
                                + parser.curriculumType()
                );
            }
        }
    }

    public CourseImportResponse importWorkbook(
            MultipartFile file,
            CurriculumType curriculumType
    ) {
        if (file == null) {
            throw new CourseWorkbookParseException(
                    "FILE_MISSING",
                    "업로드할 엑셀 파일이 없습니다."
            );
        }
        if (file.getSize() > maxUploadBytes) {
            throw new CourseWorkbookParseException(
                    "FILE_TOO_LARGE",
                    "업로드 파일이 허용 크기를 초과했습니다.",
                    Map.of("actualBytes", file.getSize(), "maxBytes", maxUploadBytes)
            );
        }

        CourseWorkbookParser parser = parsers.get(curriculumType);
        if (parser == null) {
            throw new IllegalStateException(
                    "등록된 엑셀 파서가 없습니다: " + curriculumType
            );
        }

        TimetableParseResultRequest parsed = parser.parse(readBytes(file), file.getOriginalFilename());
        return courseImportService.importCourses(parsed);
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
}
