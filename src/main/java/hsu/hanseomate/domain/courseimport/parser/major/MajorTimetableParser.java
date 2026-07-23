package hsu.hanseomate.domain.courseimport.parser.major;

import hsu.hanseomate.domain.courseimport.dto.AcademicUnitRequest;
import hsu.hanseomate.domain.courseimport.dto.LectureRequest;
import hsu.hanseomate.domain.courseimport.dto.ParseIssueRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import hsu.hanseomate.domain.courseimport.parser.common.CourseParserOutput;
import hsu.hanseomate.domain.courseimport.parser.common.CourseSheetParser;
import hsu.hanseomate.domain.courseimport.parser.common.ExcelText;
import hsu.hanseomate.domain.courseimport.parser.common.ExcelValueParser;
import hsu.hanseomate.domain.courseimport.parser.common.HeaderDetector;
import hsu.hanseomate.domain.courseimport.parser.common.HeaderMap;
import hsu.hanseomate.domain.courseimport.parser.common.ScheduleParser;
import hsu.hanseomate.domain.courseimport.parser.common.SheetView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Hanseo major-course timetable workbooks into the stable import DTO. */
public final class MajorTimetableParser implements CourseSheetParser {

    public static final String VERSION = "major-v3";

    private static final Pattern LABELED_UNIT = Pattern.compile(
            "^학과\\s*[:：]\\s*(?<department>.*?)\\s*[/|,]\\s*"
                    + "전공\\s*[:：]\\s*(?<major>.+)$"
    );
    private static final Pattern BRACKETED_UNIT = Pattern.compile(
            "^(?<department>.*?)\\s*[（(\\[](?<major>.*?)[)）\\]]\\s*$"
    );
    private static final Pattern DEPARTMENT_AND_MAJOR = Pattern.compile(
            "^(?<department>.+?(?:학과|학부))\\s+(?<major>.+전공)$"
    );
    private static final Pattern SINGLE_LABELED_UNIT = Pattern.compile(
            "^(?:학과|학부|전공)\\s*[:：]\\s*(?<unit>.+)$"
    );
    private static final Pattern LABELED_UNIT_START = Pattern.compile(
            "^(?:학과|학부|전공)\\s*[:：]\\s*\\S"
    );
    private static final Pattern UNIT_WITH_MAJOR = Pattern.compile(
            ".+?(?:학과|학부|전공|학군단|대학원|대학)\\s*[（(\\[].+?[)）\\]]"
    );
    private static final Pattern UNIT_ONLY = Pattern.compile(
            ".+(?:학과|학부|전공|학군단|대학원|대학)"
    );
    private static final Pattern GRADE = Pattern.compile("([1-6])\\s*학년");
    private static final Pattern SHEET_NAME = Pattern.compile("sheet\\d*");

    private static final Set<String> DEPARTMENT_LABELS = Set.of(
            "학과", "학과명", "학부", "학부명", "개설학과", "소속학과", "주관학과"
    );
    private static final Set<String> MAJOR_LABELS = Set.of(
            "전공", "전공명", "개설전공", "소속전공"
    );
    private static final Set<String> COMBINED_UNIT_LABELS = Set.of(
            "학과전공", "개설학과전공", "소속학과전공"
    );
    private static final List<String> GUIDE_MARKERS = List.of(
            "학년도", "강의시간표", "강좌시간표", "수업시간표", "교과목편성표", "교과편성표",
            "교육과정표", "교과과정표", "개설현황", "개설목록", "교과목목록", "과목목록",
            "안내", "공지", "유의", "주의", "필독", "참고", "통계", "집계", "합계",
            "수강불가", "수강금지", "수강제한", "수강가능", "수강대상", "수강신청",
            "신청불가", "신청대상", "이수불가", "이수대상", "전공필수", "전공선택",
            "전공기초", "전공핵심", "전공심화", "전공공통"
    );
    private static final Set<String> GUIDE_EXACT = Set.of(
            "주간", "주간반", "야간", "야간반", "전체", "총계", "필수", "선택", "전필",
            "전선", "필수과목", "선택과목", "공통과목", "일반선택"
    );

    @Override
    public CurriculumType curriculumType() {
        return CurriculumType.MAJOR;
    }

    @Override
    public String parserVersion() {
        return VERSION;
    }

    @Override
    public CourseParserOutput parse(List<SheetView> views) {
        CourseParserOutput output = new CourseParserOutput(VERSION);
        Map<UnitKey, AcademicUnitRequest> unitsByKey = new LinkedHashMap<>();

        for (SheetView view : views) {
            Map<UnitKey, AcademicUnitRequest> sheetUnitsByKey = new LinkedHashMap<>();
            boolean sheetHasSupportedHeader = false;
            int sheetLectureStart = output.lectures().size();
            HeaderMap currentHeader = null;
            AcademicUnitRequest currentUnit = unitFromSheetName(view.name());
            putUnit(sheetUnitsByKey, currentUnit);

            for (int rowNumber = 1; rowNumber <= view.maxRow(); rowNumber++) {
                HeaderMap header = HeaderDetector.detectHeader(view, rowNumber);
                if (header != null) {
                    String sectionValue = sectionBeforeHeader(view, header.startRow());
                    if (sectionValue != null) {
                        currentUnit = parseAcademicUnit(sectionValue);
                        putUnit(sheetUnitsByKey, currentUnit);
                    }
                    currentHeader = header;
                    sheetHasSupportedHeader = true;
                    output.incrementHeaderCount();
                    continue;
                }

                String standaloneValue = standaloneValue(view, rowNumber);
                if (standaloneValue != null && isGuideOrSectionLabel(standaloneValue)) {
                    continue;
                }
                if (standaloneValue != null && looksLikeAcademicUnit(standaloneValue)) {
                    currentUnit = parseAcademicUnit(standaloneValue);
                    putUnit(sheetUnitsByKey, currentUnit);
                    continue;
                }

                AcademicUnitRequest metadataUnit = unitFromMetadataRow(view, rowNumber, currentUnit);
                if (metadataUnit != null) {
                    currentUnit = metadataUnit;
                    putUnit(sheetUnitsByKey, currentUnit);
                    continue;
                }

                if (HeaderDetector.looksLikeUnsupportedHeader(view, rowNumber, currentHeader)) {
                    currentHeader = null;
                    if (isFirstPartOfSupportedHeader(view, rowNumber)) {
                        continue;
                    }
                    output.issues().add(issue(
                            IssueSeverity.ERROR,
                            "UNSUPPORTED_MAJOR_HEADER",
                            "새 전공 헤더 형식을 완전하게 인식할 수 없어 해당 블록을 저장하지 않습니다.",
                            view.name(),
                            rowNumber,
                            null,
                            String.join(" | ", view.meaningfulValues(rowNumber, false))
                    ));
                    continue;
                }

                if (currentHeader == null || rowNumber <= currentHeader.rowNumber()) {
                    continue;
                }

                Integer codeColumn = currentHeader.get("course_code");
                Integer nameColumn = currentHeader.get("course_name");
                if (codeColumn == null && nameColumn == null) {
                    continue;
                }
                String courseCodeText = codeColumn == null
                        ? ""
                        : view.text(rowNumber, codeColumn, true);
                String courseNameText = nameColumn == null
                        ? ""
                        : view.text(rowNumber, nameColumn);
                String identityHeading = identityHeading(
                        view, rowNumber, currentHeader, courseCodeText, courseNameText
                );
                if (identityHeading != null) {
                    if (looksLikeAcademicUnit(identityHeading)) {
                        currentUnit = parseAcademicUnit(identityHeading);
                        putUnit(sheetUnitsByKey, currentUnit);
                    }
                    continue;
                }
                if (!looksLikeLectureRow(
                        view, rowNumber, currentHeader, courseCodeText, courseNameText
                )) {
                    continue;
                }
                if (looksLikeSummaryRow(
                        view, rowNumber, currentHeader, courseCodeText, courseNameText
                )) {
                    continue;
                }

                AcademicUnitRequest rowUnit = unitFromColumns(
                        view, rowNumber, currentHeader, currentUnit
                );
                if (rowUnit != null) {
                    currentUnit = rowUnit;
                    putUnit(sheetUnitsByKey, currentUnit);
                }

                int rowIssueStart = output.issues().size();
                String courseCode = blankToNull(courseCodeText);
                String courseName = blankToNull(courseNameText);
                if (courseCode == null) {
                    output.issues().add(issue(
                            IssueSeverity.ERROR,
                            "MISSING_COURSE_CODE",
                            "과목코드가 비어 있어 원본 null로 보존했습니다.",
                            view.name(), rowNumber, "courseCode", null
                    ));
                }
                if (courseName == null) {
                    output.issues().add(issue(
                            IssueSeverity.ERROR,
                            "MISSING_COURSE_NAME",
                            "교과목명이 비어 있어 원본 null로 보존했습니다.",
                            view.name(), rowNumber, "courseName", null
                    ));
                }
                if (currentUnit == null) {
                    output.issues().add(issue(
                            IssueSeverity.ERROR,
                            "ACADEMIC_UNIT_NOT_FOUND",
                            "강좌가 속한 학과·전공 구역을 찾을 수 없습니다.",
                            view.name(), rowNumber, null, null
                    ));
                }

                String scheduleText = text(view, rowNumber, currentHeader, "schedule", false);
                String classroomText = text(view, rowNumber, currentHeader, "classroom", false);
                ScheduleParser.ScheduleParseResult scheduleResult = ScheduleParser.parse(
                        scheduleText, classroomText, view.name(), rowNumber
                );
                output.issues().addAll(scheduleResult.issues());

                String gradeText = text(view, rowNumber, currentHeader, "grade", false);
                GradeResult grade = parseGrade(gradeText);
                String sectionNo = text(view, rowNumber, currentHeader, "section_no", true);
                String creditText = text(view, rowNumber, currentHeader, "credit", false);
                Double credit = number(creditText);

                addMissingWarning(
                        output, sectionNo, "MISSING_SECTION_NO", "분반이 비어 있습니다.",
                        view.name(), rowNumber, "sectionNo"
                );
                addMissingWarning(
                        output, creditText, "MISSING_CREDIT", "학점이 비어 있습니다.",
                        view.name(), rowNumber, "credit"
                );
                addMissingWarning(
                        output, gradeText, "MISSING_TARGET_GRADE", "학년이 비어 있습니다.",
                        view.name(), rowNumber, "targetGrade"
                );
                if (!creditText.isEmpty() && credit == null) {
                    output.issues().add(issue(
                            IssueSeverity.ERROR,
                            "INVALID_CREDIT",
                            "학점을 숫자로 변환할 수 없습니다.",
                            view.name(), rowNumber, "credit", creditText
                    ));
                }
                if (!gradeText.isEmpty() && grade.targetGrade() == null && !grade.commonGrade()) {
                    output.issues().add(issue(
                            IssueSeverity.ERROR,
                            "INVALID_TARGET_GRADE",
                            "학년 형식을 해석할 수 없습니다.",
                            view.name(), rowNumber, "targetGrade", gradeText
                    ));
                }

                String teamTeachingText = text(
                        view, rowNumber, currentHeader, "team_teaching", false
                );
                output.lectures().add(new LectureRequest(
                        view.name(),
                        rowNumber,
                        CurriculumType.MAJOR,
                        currentUnit,
                        null,
                        courseCode,
                        courseName,
                        blankToNull(sectionNo),
                        credit,
                        null,
                        blankToNull(text(view, rowNumber, currentHeader, "instructor", false)),
                        grade.targetGrade(),
                        grade.commonGrade(),
                        List.of(),
                        List.of(),
                        teamTeachingText.isEmpty() ? null : teamTeaching(teamTeachingText),
                        blankToNull(text(view, rowNumber, currentHeader, "note", false)),
                        null,
                        blankToNull(scheduleText),
                        blankToNull(classroomText),
                        List.copyOf(scheduleResult.schedules()),
                        List.copyOf(HeaderDetector.extractSourceCells(view, rowNumber, currentHeader))
                ));
                boolean rowHasError = output.issues().subList(rowIssueStart, output.issues().size())
                        .stream()
                        .anyMatch(item -> item.severity() == IssueSeverity.ERROR);
                if (rowHasError) {
                    output.incrementFailedRowCount();
                }
            }

            if (sheetHasSupportedHeader || output.lectures().size() > sheetLectureStart) {
                sheetUnitsByKey.forEach(unitsByKey::putIfAbsent);
            }
        }

        output.academicUnits().addAll(unitsByKey.values());
        if (output.headerCount() == 0) {
            output.issues().add(issue(
                    IssueSeverity.ERROR,
                    "MAJOR_HEADER_NOT_FOUND",
                    "전공 시간표의 필수 헤더를 찾지 못했습니다.",
                    null, null, null, null
            ));
        }
        return output;
    }

    private static String sectionBeforeHeader(SheetView view, int headerRowNumber) {
        for (int row = headerRowNumber - 1; row >= Math.max(1, headerRowNumber - 49); row--) {
            List<String> rawValues = view.meaningfulValues(row, false);
            if (rawValues.isEmpty()) {
                continue;
            }
            if (rawValues.size() != 1) {
                break;
            }
            String value = stripSectionMarkers(ExcelText.normalize(rawValues.get(0)));
            if (value.isEmpty() || value.contains("학년도") || value.contains("강의시간표")) {
                continue;
            }
            if (value.startsWith("*") || value.startsWith("※") || value.contains("안내")) {
                continue;
            }
            if (looksLikeAcademicUnit(value)) {
                return value;
            }
        }
        return null;
    }

    private static String standaloneValue(SheetView view, int rowNumber) {
        List<String> values = view.meaningfulValues(rowNumber, false);
        if (values.size() != 1) {
            return null;
        }
        return blankToNull(stripSectionMarkers(ExcelText.normalize(values.get(0))));
    }

    static boolean looksLikeAcademicUnit(String value) {
        if (isGuideOrSectionLabel(value)) {
            return false;
        }
        String normalized = stripUnitMarkers(ExcelText.normalize(value));
        String compact = ExcelText.compact(normalized);
        if (SHEET_NAME.matcher(compact).matches()) {
            return false;
        }
        if (LABELED_UNIT_START.matcher(normalized).find()) {
            return true;
        }
        return UNIT_WITH_MAJOR.matcher(normalized).matches()
                || UNIT_ONLY.matcher(normalized).matches();
    }

    static AcademicUnitRequest parseAcademicUnit(String value) {
        String original = stripUnitMarkers(value == null ? "" : value.trim());
        Matcher match = LABELED_UNIT.matcher(original);
        if (!match.matches()) {
            match = BRACKETED_UNIT.matcher(original);
        }
        if (!match.matches()) {
            match = DEPARTMENT_AND_MAJOR.matcher(original);
        }
        if (match.matches()) {
            String department = ExcelText.normalize(match.group("department"));
            String major = blankToNull(ExcelText.normalize(match.group("major")));
            return new AcademicUnitRequest(original, department, major);
        }
        Matcher single = SINGLE_LABELED_UNIT.matcher(original);
        String department = single.matches()
                ? ExcelText.normalize(single.group("unit"))
                : original;
        return new AcademicUnitRequest(original, department, null);
    }

    private static AcademicUnitRequest unitFromColumns(
            SheetView view,
            int rowNumber,
            HeaderMap header,
            AcademicUnitRequest current
    ) {
        String combined = text(view, rowNumber, header, "academic_unit", false);
        if (!combined.isEmpty()) {
            return parseAcademicUnit(combined);
        }
        String department = text(view, rowNumber, header, "department", false);
        String major = text(view, rowNumber, header, "major", false);
        if (department.isEmpty() && major.isEmpty()) {
            return null;
        }
        String effectiveDepartment = !department.isEmpty()
                ? department
                : current == null ? "" : current.departmentName();
        String effectiveMajor = !major.isEmpty()
                ? major
                : current != null && department.isEmpty() ? current.majorName() : null;
        if (effectiveDepartment.isEmpty()) {
            effectiveDepartment = !major.isEmpty() ? major : "미확인 학과";
            effectiveMajor = null;
        }
        String original = effectiveMajor == null
                ? effectiveDepartment
                : effectiveDepartment + "(" + effectiveMajor + ")";
        return new AcademicUnitRequest(original, effectiveDepartment, effectiveMajor);
    }

    private static AcademicUnitRequest unitFromMetadataRow(
            SheetView view,
            int rowNumber,
            AcademicUnitRequest current
    ) {
        List<String> values = view.meaningfulValues(rowNumber, false);
        if (values.size() != 2) {
            return null;
        }
        String label = ExcelText.compact(values.get(0));
        String value = values.get(1);
        if (DEPARTMENT_LABELS.contains(label)) {
            return parseAcademicUnit(value);
        }
        if (MAJOR_LABELS.contains(label)) {
            String department = current == null ? value : current.departmentName();
            String major = current == null ? null : ExcelText.normalize(value);
            return new AcademicUnitRequest(
                    major == null ? department : department + "(" + major + ")",
                    department,
                    major
            );
        }
        if (COMBINED_UNIT_LABELS.contains(label)) {
            return parseAcademicUnit(value);
        }
        return null;
    }

    private static AcademicUnitRequest unitFromSheetName(String sheetName) {
        String value = ExcelText.normalize(sheetName);
        return looksLikeAcademicUnit(value) ? parseAcademicUnit(value) : null;
    }

    static boolean isGuideOrSectionLabel(String value) {
        String normalized = ExcelText.normalize(value);
        String compact = ExcelText.compact(normalized);
        if (normalized.startsWith("*") || normalized.startsWith("※")) {
            return true;
        }
        if (GUIDE_MARKERS.stream().anyMatch(compact::contains)) {
            return true;
        }
        return GUIDE_EXACT.contains(compact);
    }

    private static String text(
            SheetView view,
            int rowNumber,
            HeaderMap header,
            String field,
            boolean identifier
    ) {
        Integer column = header.get(field);
        return column == null ? "" : view.text(rowNumber, column, identifier);
    }

    private static GradeResult parseGrade(String value) {
        String normalized = ExcelText.normalize(value);
        if (normalized.isEmpty()) {
            return new GradeResult(null, false);
        }
        if (normalized.contains("공통") || normalized.contains("전학년")) {
            return new GradeResult(null, true);
        }
        Matcher matcher = GRADE.matcher(normalized);
        return matcher.find()
                ? new GradeResult(Integer.parseInt(matcher.group(1)), false)
                : new GradeResult(null, false);
    }

    private static boolean looksLikeLectureRow(
            SheetView view,
            int rowNumber,
            HeaderMap header,
            String courseCode,
            String courseName
    ) {
        if (!courseCode.isEmpty() || !courseName.isEmpty()) {
            return true;
        }
        int populated = 0;
        for (String field : List.of(
                "grade", "section_no", "credit", "instructor", "schedule",
                "classroom", "team_teaching"
        )) {
            if (!text(view, rowNumber, header, field, false).isEmpty()) {
                populated++;
            }
        }
        return populated >= 3;
    }

    /**
     * 전 열을 병합한 학과·구역 제목은 병합 셀 해석 과정에서 학수번호와 과목명
     * 열 모두에 같은 값이 나타난다. 이를 실제 강좌로 저장하지 않도록 구분한다.
     */
    private static String identityHeading(
            SheetView view,
            int rowNumber,
            HeaderMap header,
            String courseCode,
            String courseName
    ) {
        String normalizedCode = ExcelText.normalize(courseCode);
        String normalizedName = ExcelText.normalize(courseName);
        if (normalizedCode.isEmpty() || !normalizedCode.equals(normalizedName)) {
            return null;
        }
        String compact = ExcelText.compact(normalizedCode);
        if (compact.matches("[0-9a-z]+") && compact.chars().anyMatch(Character::isDigit)) {
            return null;
        }
        for (String field : List.of(
                "grade", "section_no", "credit", "class_hours", "instructor", "schedule",
                "classroom", "team_teaching", "note", "eligibility"
        )) {
            String value = text(view, rowNumber, header, field, false);
            if (!value.isEmpty() && !ExcelText.normalize(value).equals(normalizedCode)) {
                return null;
            }
        }
        return normalizedCode;
    }

    private static boolean looksLikeSummaryRow(
            SheetView view,
            int rowNumber,
            HeaderMap header,
            String courseCode,
            String courseName
    ) {
        String identity = ExcelText.compact(courseCode + " " + courseName);
        if (List.of("total", "subtotal", "합계", "소계", "총계", "집계")
                .stream().noneMatch(identity::contains)) {
            return false;
        }
        return List.of("grade", "section_no", "schedule", "classroom").stream()
                .noneMatch(field -> !text(view, rowNumber, header, field, false).isEmpty());
    }

    private static boolean isFirstPartOfSupportedHeader(SheetView view, int rowNumber) {
        if (rowNumber >= view.maxRow()) {
            return false;
        }
        int finalCandidate = Math.min(
                view.maxRow(),
                rowNumber + HeaderDetector.MAX_HEADER_ROWS - 1
        );
        for (int candidate = rowNumber + 1; candidate <= finalCandidate; candidate++) {
            HeaderMap header = HeaderDetector.detectHeader(view, candidate);
            if (header != null && header.startRow() <= rowNumber) {
                return true;
            }
        }
        return false;
    }

    private static Double number(String value) {
        return ExcelValueParser.parseNumber(value);
    }

    private static boolean teamTeaching(String value) {
        String normalized = ExcelText.normalize(value).toLowerCase();
        return Set.of("y", "yes", "o", "true", "예", "팀티칭", "해당").contains(normalized)
                || (normalized.contains("팀티칭") && !normalized.contains("아님"));
    }

    private static void addMissingWarning(
            CourseParserOutput output,
            String value,
            String code,
            String message,
            String sheet,
            int row,
            String field
    ) {
        if (value == null || value.isEmpty()) {
            output.issues().add(issue(
                    IssueSeverity.WARNING,
                    code,
                    message + " 원본 null로 보존했습니다.",
                    sheet,
                    row,
                    field,
                    null
            ));
        }
    }

    private static ParseIssueRequest issue(
            IssueSeverity severity,
            String code,
            String message,
            String sheet,
            Integer row,
            String field,
            String rawValue
    ) {
        return new ParseIssueRequest(severity, code, message, sheet, row, field, rawValue);
    }

    private static void putUnit(
            Map<UnitKey, AcademicUnitRequest> target,
            AcademicUnitRequest unit
    ) {
        if (unit != null) {
            target.putIfAbsent(new UnitKey(unit.departmentName(), unit.majorName()), unit);
        }
    }

    private static String stripSectionMarkers(String value) {
        return value.replaceFirst("^[▶▷■●]+", "").trim();
    }

    private static String stripUnitMarkers(String value) {
        return value.replaceFirst("^[▶▷■●◆◇▣]+", "").trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record UnitKey(String departmentName, String majorName) {
    }

    private record GradeResult(Integer targetGrade, boolean commonGrade) {
    }
}
