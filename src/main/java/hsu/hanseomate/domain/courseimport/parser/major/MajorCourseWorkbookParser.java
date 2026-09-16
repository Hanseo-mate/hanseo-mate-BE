package hsu.hanseomate.domain.courseimport.parser.major;

import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseEngine;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParser;
import org.springframework.stereotype.Component;

/** Complete major-course workbook parser used by the multipart import facade. */
@Component
public final class MajorCourseWorkbookParser implements CourseWorkbookParser {

    private final CourseWorkbookParseEngine engine;
    private final MajorTimetableParser sheetParser;

    public MajorCourseWorkbookParser() {
        this(new CourseWorkbookParseEngine(), new MajorTimetableParser());
    }

    MajorCourseWorkbookParser(
            CourseWorkbookParseEngine engine,
            MajorTimetableParser sheetParser
    ) {
        this.engine = engine;
        this.sheetParser = sheetParser;
    }

    @Override
    public CurriculumType curriculumType() {
        return CurriculumType.MAJOR;
    }

    @Override
    public TimetableParseResultRequest parse(byte[] fileBytes, String fileName) {
        return engine.parse(fileBytes, fileName, sheetParser);
    }
}
