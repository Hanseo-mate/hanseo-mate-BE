package hsu.hanseomate.domain.courseimport.parser.common;

import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;

/** A complete workbook parser for one curriculum type. */
public interface CourseWorkbookParser {

    CurriculumType curriculumType();

    TimetableParseResultRequest parse(byte[] fileBytes, String fileName);
}
