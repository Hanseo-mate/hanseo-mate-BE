package hsu.hanseomate.domain.courseimport.parser.common;

import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import java.util.List;

/**
 * Parses already-loaded worksheets. Both major and general-education parsers
 * share this contract.
 */
public interface CourseSheetParser {

    CurriculumType curriculumType();

    String parserVersion();

    CourseParserOutput parse(List<SheetView> sheets);
}
