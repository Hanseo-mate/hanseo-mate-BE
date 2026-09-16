package hsu.hanseomate.domain.courseimport.parser.general;

import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseEngine;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParser;
import org.springframework.stereotype.Component;

/** 교양 강좌 엑셀을 공통 워크북 처리 흐름과 교양 전용 규칙으로 파싱한다. */
@Component
public final class GeneralCourseWorkbookParser implements CourseWorkbookParser {

    private final CourseWorkbookParseEngine engine;
    private final GeneralTimetableParser sheetParser;

    public GeneralCourseWorkbookParser() {
        this(new CourseWorkbookParseEngine(), new GeneralTimetableParser());
    }

    GeneralCourseWorkbookParser(
            CourseWorkbookParseEngine engine,
            GeneralTimetableParser sheetParser
    ) {
        this.engine = engine;
        this.sheetParser = sheetParser;
    }

    @Override
    public CurriculumType curriculumType() {
        return CurriculumType.GENERAL_EDUCATION;
    }

    @Override
    public TimetableParseResultRequest parse(byte[] fileBytes, String fileName) {
        return engine.parse(fileBytes, fileName, sheetParser);
    }
}
