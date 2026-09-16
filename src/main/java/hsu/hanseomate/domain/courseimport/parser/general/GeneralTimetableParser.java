package hsu.hanseomate.domain.courseimport.parser.general;

import hsu.hanseomate.domain.courseimport.dto.GeneralCategoryNodeRequest;
import hsu.hanseomate.domain.courseimport.dto.GeneralEducationContextRequest;
import hsu.hanseomate.domain.courseimport.dto.LectureRequest;
import hsu.hanseomate.domain.courseimport.dto.ParseIssueRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralCategoryNodeType;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import hsu.hanseomate.domain.courseimport.parser.common.CourseParserOutput;
import hsu.hanseomate.domain.courseimport.parser.common.CourseSheetParser;
import hsu.hanseomate.domain.courseimport.parser.common.ExcelText;
import hsu.hanseomate.domain.courseimport.parser.common.ExcelValueParser;
import hsu.hanseomate.domain.courseimport.parser.common.HeaderDetector;
import hsu.hanseomate.domain.courseimport.parser.common.HeaderMap;
import hsu.hanseomate.domain.courseimport.parser.common.ScheduleParser;
import hsu.hanseomate.domain.courseimport.parser.common.SheetView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Hanseo general-education workbooks. Unrecognised taxonomy headings
 * are preserved so future workbook formats require review instead of
 * silently losing information.
 */
public final class GeneralTimetableParser implements CourseSheetParser {

    public static final String VERSION = "general-v3";

    private static final Pattern COURSE_CODE = Pattern.compile("^\\d{7}$");
    private static final Pattern UNIT_SUFFIX = Pattern.compile("(?:학과|학부)(?:\\s*\\([^)]*\\))?$");
    private static final Pattern TOP_LEVEL_MARKER = Pattern.compile("^\\s*[▶▷■●◆◇▣]");
    private static final Pattern CLASSIFICATION_REQUIRED = Pattern.compile("(?:\\d+)?교양필수(?:강좌|과정|영역)?");
    private static final Pattern CLASSIFICATION_ELECTIVE = Pattern.compile("(?:\\d+)?교양선택(?:강좌|과정|영역)?");
    private static final Pattern OCU = Pattern.compile("(?i)(?<![A-Za-z])ocu(?![A-Za-z])");
    private static final Pattern ACADEMIC_YEAR_TITLE = Pattern.compile("\\d{4}학년도[12]학기");
    private static final Pattern CATEGORY_HEADER = Pattern.compile(
            "(?:대분류|중분류|소분류|세부분류|교양분류|교양카테고리|카테고리|분류[123]|[123]차분류|트랙)"
    );
    private static final Pattern CATEGORY_PATH_SEPARATOR = Pattern.compile("\\s*(?:>|›|→|\\|)\\s*");
    private static final Pattern CATEGORY_LEVEL_LABEL = Pattern.compile(
            "(?:대|중|소|세부|[123]차)?(?:분류|카테고리)(?:단계|레벨)?"
    );
    private static final Pattern GRADE_RANGE = Pattern.compile("([1-4])\\s*학년?\\s*[~-]\\s*([1-4])\\s*학년");
    private static final Pattern GRADE_ONLY = Pattern.compile("([1-4])\\s*학년\\s*만");
    private static final Pattern PROVIDER_RESTRICTION = Pattern.compile("\\(([^()]*(?:학년|수강)[^()]*)\\)");
    private static final List<String> RESTRICTION_WORDS = List.of(
            "학년", "학번", "수강", "전용강좌", "로그인", "비밀번호"
    );
    private static final Map<String, String> REQUIRED_CATEGORY_CODES = Map.ofEntries(
            Map.entry("지역사회와봉사", "COMMUNITY_SERVICE"),
            Map.entry("진로탐색과사회진출전략", "CAREER_EXPLORATION"),
            Map.entry("대학생활세미나", "UNIVERSITY_LIFE_SEMINAR"),
            Map.entry("englishlisteningandspeaking", "ENGLISH_LISTENING_SPEAKING"),
            Map.entry("융합적사고와글쓰기", "CONVERGENT_THINKING_AND_WRITING"),
            Map.entry("외국인유학생교양필수", "INTERNATIONAL_STUDENT_REQUIRED")
    );

    @Override
    public CurriculumType curriculumType() {
        return CurriculumType.GENERAL_EDUCATION;
    }

    @Override
    public String parserVersion() {
        return VERSION;
    }

    @Override
    public CourseParserOutput parse(List<SheetView> sheets) {
        CourseParserOutput output = new CourseParserOutput(VERSION);
        Map<GeneralEducationContextRequest, GeneralEducationContextRequest> categories =
                new LinkedHashMap<>();
        Map<String, GeneralCategoryNodeRequest> categoryNodes = new LinkedHashMap<>();
        Set<RowIdentity> failedRows = new LinkedHashSet<>();

        for (SheetView sheet : sheets) {
            parseSheet(sheet, output, categories, categoryNodes, failedRows);
        }

        if (output.headerCount() == 0) {
            output.issues().add(new ParseIssueRequest(
                    IssueSeverity.ERROR,
                    "GENERAL_HEADER_NOT_FOUND",
                    "워크북 전체에서 교양 시간표의 강좌 헤더를 찾을 수 없습니다.",
                    null,
                    null,
                    null,
                    null
            ));
        }
        output.generalCategories().addAll(categories.values());
        output.generalCategoryNodes().addAll(categoryNodes.values());
        for (int index = 0; index < failedRows.size(); index++) {
            output.incrementFailedRowCount();
        }
        return output;
    }

    private void parseSheet(
            SheetView view,
            CourseParserOutput output,
            Map<GeneralEducationContextRequest, GeneralEducationContextRequest> categories,
            Map<String, GeneralCategoryNodeRequest> categoryNodes,
            Set<RowIdentity> failedRows
    ) {
        ParseState state = stateFromSheetName(view.name());
        Map<String, GeneralCategoryNodeRequest> sheetNodes = new LinkedHashMap<>();
        boolean sheetHasSupportedContent = false;
        int lectureCountBefore = output.lectures().size();

        for (int rowNumber = 1; rowNumber <= view.maxRow(); rowNumber++) {
            HeaderMap header = HeaderDetector.detectHeader(view, rowNumber);
            if (header != null) {
                state.header = header;
                output.incrementHeaderCount();
                sheetHasSupportedContent = true;
                continue;
            }

            List<String> values = view.meaningfulValues(rowNumber, false);
            if (values.isEmpty()) {
                continue;
            }

            String ambiguousMergedField = values.size() == 1
                    ? ambiguousMergedIdentityField(view, rowNumber, state.header)
                    : null;
            if (ambiguousMergedField != null) {
                output.issues().add(issue(
                        view,
                        rowNumber,
                        IssueSeverity.ERROR,
                        "AMBIGUOUS_MERGED_LECTURE_ROW",
                        "강좌 식별 셀이 일부 열에만 병합되어 제목 행인지 강좌 행인지 확인이 필요합니다.",
                        ambiguousMergedField,
                        values.get(0)
                ));
                failedRows.add(new RowIdentity(view.name(), rowNumber));
                continue;
            }

            if (values.size() == 1 && !hasDirectIdentityValue(view, rowNumber, state.header)) {
                MarkerResult marker = consumeMarker(
                        values.get(0),
                        state,
                        output,
                        hasChildHeading(view, rowNumber),
                        view.name(),
                        rowNumber
                );
                if (marker.consumed()) {
                    if (marker.nodeType() != null) {
                        registerCategoryNode(
                                sheetNodes,
                                marker.nodeType(),
                                state,
                                view.name(),
                                rowNumber
                        );
                    }
                    continue;
                }
            }

            if (HeaderDetector.looksLikeUnsupportedHeader(view, rowNumber, state.header)) {
                if (isFirstHalfOfSupportedHeader(view, rowNumber)) {
                    state.header = null;
                    continue;
                }
                state.header = null;
                output.issues().add(issue(
                        view,
                        rowNumber,
                        IssueSeverity.ERROR,
                        "UNSUPPORTED_GENERAL_HEADER",
                        "새 헤더 형식을 완전하게 인식할 수 없어 해당 블록을 저장하지 않습니다.",
                        null,
                        String.join(" | ", values)
                ));
                continue;
            }

            if (state.header == null) {
                continue;
            }

            LectureResult result = parseLecture(view, rowNumber, state, categories);
            if (result.lecture() == null) {
                List<ParseIssueRequest> issues = new ArrayList<>(result.issues());
                if (looksLikeDataRow(view, rowNumber, state.header)) {
                    issues.add(issue(
                            view,
                            rowNumber,
                            IssueSeverity.ERROR,
                            "INVALID_GENERAL_LECTURE_ROW",
                            "교양 강좌 행에서 유효한 과목코드와 교과목명을 찾을 수 없습니다.",
                            null,
                            null
                    ));
                    failedRows.add(new RowIdentity(view.name(), rowNumber));
                }
                output.issues().addAll(issues);
                continue;
            }

            output.lectures().add(result.lecture());
            state.blockLectureIndices.add(output.lectures().size() - 1);
            ensureContextNodes(
                    sheetNodes,
                    result.lecture().generalEducation(),
                    result.contextParts(),
                    view.name(),
                    rowNumber
            );
            output.issues().addAll(result.issues());
            if (result.issues().stream().anyMatch(item -> item.severity() == IssueSeverity.ERROR)) {
                failedRows.add(new RowIdentity(view.name(), rowNumber));
            }
        }

        sheetHasSupportedContent = sheetHasSupportedContent
                || output.lectures().size() > lectureCountBefore;
        if (sheetHasSupportedContent) {
            for (Map.Entry<String, GeneralCategoryNodeRequest> entry : sheetNodes.entrySet()) {
                if (categoryNodes.containsKey(entry.getKey())) {
                    continue;
                }
                GeneralCategoryNodeRequest node = entry.getValue();
                categoryNodes.put(entry.getKey(), copyNodeWithSortOrder(node, categoryNodes.size() + 1));
            }
        }
    }

    private MarkerResult consumeMarker(
            String value,
            ParseState state,
            CourseParserOutput output,
            boolean hasChildHeading,
            String sheetName,
            int rowNumber
    ) {
        if (isTitle(value)) {
            return MarkerResult.notConsumed();
        }
        String compact = ExcelText.compact(value);
        GeneralClassification classification = classificationMarker(compact);
        if (classification != null) {
            state.classification = classification;
            state.classificationName = cleanMarkerName(value);
            state.categoryName = null;
            state.area = null;
            state.provider = DeliveryProvider.ON_CAMPUS;
            state.providerHeading = null;
            state.blockLectureIndices.clear();
            state.headingParts.clear();
            return MarkerResult.consumed(GeneralCategoryNodeType.CLASSIFICATION);
        }

        DeliveryProvider provider = providerMarker(value);
        if (provider != null) {
            ensureElectiveClassification(state);
            state.categoryName = null;
            state.area = null;
            state.provider = provider;
            state.providerHeading = ExcelText.normalize(value);
            state.blockLectureIndices.clear();
            state.headingParts = new ArrayList<>(List.of(new PathPart(
                    state.providerHeading,
                    GeneralCategoryNodeType.PROVIDER,
                    null,
                    provider,
                    provider == DeliveryProvider.OTHER ? state.providerHeading : null
            )));
            return MarkerResult.consumed(GeneralCategoryNodeType.PROVIDER);
        }

        if (isStandaloneNote(value)) {
            applyBlockNote(value, state, output);
            return MarkerResult.consumed(null);
        }
        if (state.classification == GeneralClassification.UNKNOWN
                && state.classificationName == null
                && isPlainGuide(value)) {
            return MarkerResult.consumed(null);
        }

        GeneralArea area = area(value);
        if (area != null && Set.of(
                GeneralClassification.ELECTIVE,
                GeneralClassification.UNKNOWN
        ).contains(state.classification)) {
            ensureElectiveClassification(state);
            state.categoryName = ExcelText.normalize(value);
            state.area = area;
            PathPart areaPart = new PathPart(
                    state.categoryName,
                    GeneralCategoryNodeType.AREA,
                    area,
                    null,
                    null
            );
            if (!state.headingParts.isEmpty()
                    && state.headingParts.get(0).nodeType() == GeneralCategoryNodeType.PROVIDER
                    && state.blockLectureIndices.isEmpty()) {
                state.headingParts = new ArrayList<>(List.of(state.headingParts.get(0), areaPart));
            } else {
                state.provider = DeliveryProvider.ON_CAMPUS;
                state.providerHeading = null;
                state.headingParts = new ArrayList<>(List.of(areaPart));
            }
            state.blockLectureIndices.clear();
            return MarkerResult.consumed(GeneralCategoryNodeType.AREA);
        }

        boolean explicitTopLevel = TOP_LEVEL_MARKER.matcher(value).find();
        boolean startsNewDynamicClassification = !state.blockLectureIndices.isEmpty()
                && (explicitTopLevel
                || (hasChildHeading && state.classification == GeneralClassification.UNKNOWN));
        if (startsNewDynamicClassification) {
            if (!explicitTopLevel) {
                output.issues().add(new ParseIssueRequest(
                        IssueSeverity.ERROR,
                        "AMBIGUOUS_GENERAL_HEADING_LEVEL",
                        "새 최상위 교양 분류인지 기존 분류의 소분류인지 단정할 수 없어 원문 구조를 보존하고 검토가 필요합니다.",
                        sheetName,
                        rowNumber,
                        null,
                        value
                ));
            }
            state.classification = GeneralClassification.UNKNOWN;
            state.classificationName = cleanMarkerName(value);
            state.categoryName = null;
            state.area = null;
            state.provider = DeliveryProvider.ON_CAMPUS;
            state.providerHeading = null;
            state.blockLectureIndices.clear();
            state.headingParts.clear();
            return MarkerResult.consumed(GeneralCategoryNodeType.CLASSIFICATION);
        }

        if (state.classification == GeneralClassification.UNKNOWN && state.classificationName == null) {
            state.classificationName = cleanMarkerName(value);
            state.categoryName = null;
            state.area = null;
            state.provider = DeliveryProvider.ON_CAMPUS;
            state.providerHeading = null;
            state.blockLectureIndices.clear();
            state.headingParts.clear();
            return MarkerResult.consumed(GeneralCategoryNodeType.CLASSIFICATION);
        }

        if (state.classification == GeneralClassification.REQUIRED && !isTitle(value)) {
            state.categoryName = ExcelText.normalize(value);
            state.area = null;
            setCategoryHeading(state, output, state.categoryName, sheetName, rowNumber);
            return MarkerResult.consumed(GeneralCategoryNodeType.CATEGORY);
        }
        if (state.classification == GeneralClassification.ELECTIVE && !isTitle(value)) {
            state.categoryName = ExcelText.normalize(value);
            state.area = GeneralArea.OTHER;
            setCategoryHeading(state, output, state.categoryName, sheetName, rowNumber);
            return MarkerResult.consumed(GeneralCategoryNodeType.CATEGORY);
        }
        if (state.classification == GeneralClassification.UNKNOWN && state.classificationName != null) {
            if (!state.blockLectureIndices.isEmpty()) {
                output.issues().add(new ParseIssueRequest(
                        IssueSeverity.ERROR,
                        "AMBIGUOUS_GENERAL_HEADING_LEVEL",
                        "새 최상위 교양 분류인지 기존 분류의 소분류인지 단정할 수 없어 현재 분류의 소분류로 보존하고 검토가 필요합니다.",
                        sheetName,
                        rowNumber,
                        null,
                        value
                ));
            }
            state.categoryName = ExcelText.normalize(value);
            state.area = null;
            setCategoryHeading(state, output, state.categoryName, sheetName, rowNumber);
            return MarkerResult.consumed(GeneralCategoryNodeType.CATEGORY);
        }
        return MarkerResult.notConsumed();
    }

    private static void ensureElectiveClassification(ParseState state) {
        if (state.classification == GeneralClassification.UNKNOWN && state.classificationName == null) {
            state.classification = GeneralClassification.ELECTIVE;
            state.classificationName = "교양선택";
        }
    }

    private static void setCategoryHeading(
            ParseState state,
            CourseParserOutput output,
            String name,
            String sheetName,
            int rowNumber
    ) {
        PathPart part = new PathPart(name, GeneralCategoryNodeType.CATEGORY, null, null, null);
        if (!state.blockLectureIndices.isEmpty()) {
            List<PathPart> replacement = new ArrayList<>();
            if (!state.headingParts.isEmpty()
                    && state.headingParts.get(0).nodeType() == GeneralCategoryNodeType.PROVIDER) {
                replacement.add(state.headingParts.get(0));
            }
            replacement.add(part);
            state.headingParts = replacement;
        } else if (!state.headingParts.isEmpty()) {
            output.issues().add(new ParseIssueRequest(
                    IssueSeverity.ERROR,
                    "AMBIGUOUS_HEADING_HIERARCHY",
                    "연속된 독립 제목의 상하 관계를 단정할 수 없어 원문 순서대로 계층을 보존하고 검토가 필요합니다.",
                    sheetName,
                    rowNumber,
                    null,
                    name
            ));
            state.headingParts.add(part);
        } else {
            state.headingParts = new ArrayList<>(List.of(part));
        }
        if (state.headingParts.stream().noneMatch(
                item -> item.nodeType() == GeneralCategoryNodeType.PROVIDER
        )) {
            state.provider = DeliveryProvider.ON_CAMPUS;
            state.providerHeading = null;
        }
        state.blockLectureIndices.clear();
    }

    private LectureResult parseLecture(
            SheetView view,
            int rowNumber,
            ParseState state,
            Map<GeneralEducationContextRequest, GeneralEducationContextRequest> categories
    ) {
        HeaderMap header = Objects.requireNonNull(state.header);
        List<ParseIssueRequest> issues = new ArrayList<>();
        String courseCodeText = read(view, rowNumber, header, "course_code", true);
        String normalizedCourseCode = normalizeCourseCode(courseCodeText);
        String courseCode = normalizedCourseCode.isEmpty() ? null : normalizedCourseCode;
        String courseNameText = read(view, rowNumber, header, "course_name", false);
        String courseName = courseNameText.isEmpty() ? null : courseNameText;

        if (!looksLikeDataRow(view, rowNumber, header)) {
            return new LectureResult(null, issues, List.of());
        }
        if (courseCode == null) {
            issues.add(issue(view, rowNumber, IssueSeverity.ERROR, "MISSING_COURSE_CODE",
                    "과목코드가 비어 있어 원본 null로 보존했습니다.", "courseCode", null));
        }
        if (courseName == null) {
            issues.add(issue(view, rowNumber, IssueSeverity.ERROR, "MISSING_COURSE_NAME",
                    "교과목명이 비어 있어 원본 null로 보존했습니다.", "courseName", null));
        }
        if (courseCode != null && !COURSE_CODE.matcher(courseCode).matches()) {
            issues.add(issue(view, rowNumber, IssueSeverity.WARNING, "NON_STANDARD_COURSE_CODE",
                    "7자리 숫자가 아닌 과목코드를 원문 그대로 보존했습니다.",
                    "courseCode", courseCodeText));
        }

        ClassificationResult rowClassification = rowClassification(view, rowNumber, header);
        GeneralClassification classification = rowClassification.classification() == null
                ? state.classification : rowClassification.classification();
        String detectedClassificationName = firstNonNull(
                rowClassification.name(), state.classificationName
        );
        if (classification == GeneralClassification.UNKNOWN && detectedClassificationName == null) {
            issues.add(issue(view, rowNumber, IssueSeverity.ERROR, "MISSING_GENERAL_CLASSIFICATION",
                    "교양필수 또는 교양선택 분류를 결정할 수 없습니다.", null, null));
        }
        String classificationName = detectedClassificationName == null
                ? classificationName(classification) : detectedClassificationName;

        List<PathPart> rowParts = rowTaxonomyParts(view, rowNumber, header);
        boolean classificationChanged = rowClassification.name() != null
                && state.classificationName != null
                && !rowClassification.name().equals(state.classificationName);
        List<PathPart> contextParts = new ArrayList<>(
                classificationChanged ? List.of() : state.headingParts
        );
        contextParts.addAll(rowParts);
        contextParts = deduplicatePathParts(contextParts);

        PathPart areaPart = findLastPart(contextParts, Set.of(GeneralCategoryNodeType.AREA));
        GeneralArea generalArea = areaPart == null ? state.area : areaPart.area();
        PathPart categoryPart = findLastPart(contextParts, Set.of(
                GeneralCategoryNodeType.AREA,
                GeneralCategoryNodeType.CATEGORY
        ));
        String categoryName = categoryPart == null ? state.categoryName : categoryPart.name();
        if (classification == GeneralClassification.ELECTIVE && generalArea == null) {
            generalArea = GeneralArea.OTHER;
            issues.add(issue(view, rowNumber, IssueSeverity.WARNING, "GENERAL_AREA_NOT_RECOGNIZED",
                    "교양선택 영역을 인식하지 못해 OTHER로 보존했습니다.",
                    "generalArea", categoryName));
        }

        String scheduleText = read(view, rowNumber, header, "schedule", false);
        String classroomText = read(view, rowNumber, header, "classroom", false);
        PathPart providerPart = findLastPart(contextParts, Set.of(GeneralCategoryNodeType.PROVIDER));
        DeliveryProvider baseProvider = providerPart == null ? state.provider : providerPart.provider();
        ProviderResult effectiveProvider = effectiveProvider(
                baseProvider == null ? DeliveryProvider.ON_CAMPUS : baseProvider,
                scheduleText,
                classroomText
        );
        DeliveryProvider provider = effectiveProvider.provider();
        String providerName = providerPart == null ? null : providerPart.providerName();
        if (provider == DeliveryProvider.OTHER) {
            providerName = firstNonNull(providerName, state.providerHeading, effectiveProvider.name());
        }
        if (effectiveProvider.name() != null && providerPart == null) {
            contextParts.add(0, new PathPart(
                    effectiveProvider.name(),
                    GeneralCategoryNodeType.PROVIDER,
                    null,
                    provider,
                    effectiveProvider.name()
            ));
            contextParts = deduplicatePathParts(contextParts);
        }

        GeneralEducationContextRequest context = context(
                classification,
                classificationName,
                categoryName,
                generalArea,
                provider,
                providerName,
                contextParts
        );
        GeneralEducationContextRequest canonicalContext = categories.computeIfAbsent(
                context, ignored -> context
        );

        String gradeText = read(view, rowNumber, header, "grade", false);
        GradeResult grade = grade(gradeText);
        String eligibilityRaw = read(view, rowNumber, header, "eligibility", false);
        String noteRaw = read(view, rowNumber, header, "note", false);
        String providerRestriction = providerRestriction(
                providerPart == null ? state.providerHeading : providerPart.name()
        );
        List<String> restrictions = new ArrayList<>();
        if (!providerRestriction.isEmpty()) restrictions.add(providerRestriction);
        if (!noteRaw.isEmpty()) restrictions.add(noteRaw);
        List<Integer> allowedGrades = constrainAllowedGrades(grade.allowed(), restrictions);
        boolean commonGrade = grade.common();
        if (gradeText.isEmpty() && allowedGrades.equals(List.of(1, 2, 3, 4))) {
            commonGrade = true;
        }
        List<String> eligibilityNoteParts = new ArrayList<>(List.of(eligibilityRaw));
        if (!providerRestriction.isEmpty()) eligibilityNoteParts.add(providerRestriction);
        if (!noteRaw.isEmpty() && looksLikeRestriction(noteRaw)) eligibilityNoteParts.add(noteRaw);
        String eligibilityNote = joinUnique(eligibilityNoteParts);

        ScheduleParser.ScheduleParseResult scheduleResult = ScheduleParser.parse(
                scheduleText,
                classroomText,
                view.name(),
                rowNumber
        );
        issues.addAll(scheduleResult.issues());

        String creditText = read(view, rowNumber, header, "credit", false);
        String classHoursText = read(view, rowNumber, header, "class_hours", false);
        Double credit = number(creditText);
        Double classHours = number(classHoursText);
        if (!creditText.isEmpty() && credit == null) {
            issues.add(issue(view, rowNumber, IssueSeverity.ERROR, "INVALID_CREDIT",
                    "학점을 숫자로 변환할 수 없습니다.", "credit", creditText));
        }
        if (!classHoursText.isEmpty() && classHours == null) {
            issues.add(issue(view, rowNumber, IssueSeverity.ERROR, "INVALID_CLASS_HOURS",
                    "시수를 숫자로 변환할 수 없습니다.", "classHours", classHoursText));
        }

        String instructorText = read(view, rowNumber, header, "instructor", false);
        String instructor = instructorText.isEmpty() ? null : instructorText;
        String teamText = read(view, rowNumber, header, "team_teaching", false);
        Boolean teamTeaching = null;
        if (!teamText.isEmpty() || (instructor != null && instructor.contains(","))) {
            teamTeaching = truthy(teamText) || (instructor != null && instructor.contains(","));
        }
        String section = read(view, rowNumber, header, "section_no", true);

        LectureRequest lecture = new LectureRequest(
                view.name(),
                rowNumber,
                CurriculumType.GENERAL_EDUCATION,
                null,
                canonicalContext,
                courseCode,
                courseName,
                section.isEmpty() ? null : section,
                credit,
                classHours,
                instructor,
                grade.target(),
                commonGrade,
                List.copyOf(allowedGrades),
                List.copyOf(eligibleDepartments(eligibilityRaw)),
                teamTeaching,
                noteRaw.isEmpty() ? null : noteRaw,
                eligibilityNote.isEmpty() ? null : eligibilityNote,
                scheduleText.isEmpty() ? null : scheduleText,
                classroomText.isEmpty() ? null : classroomText,
                List.copyOf(scheduleResult.schedules()),
                List.copyOf(HeaderDetector.extractSourceCells(view, rowNumber, header))
        );
        return new LectureResult(lecture, List.copyOf(issues), List.copyOf(contextParts));
    }

    private static List<PathPart> rowTaxonomyParts(SheetView view, int rowNumber, HeaderMap header) {
        List<RankedPart> entries = new ArrayList<>();
        Integer classificationColumn = header.get("classification");
        Integer providerColumn = header.get("provider");
        Integer areaColumn = header.get("area");

        if (providerColumn != null) {
            String rawProvider = view.text(rowNumber, providerColumn);
            if (!rawProvider.isEmpty()) {
                DeliveryProvider provider = providerMarker(rawProvider);
                if (provider == null) provider = DeliveryProvider.OTHER;
                entries.add(new RankedPart(0, providerColumn, new PathPart(
                        rawProvider,
                        GeneralCategoryNodeType.PROVIDER,
                        null,
                        provider,
                        provider == DeliveryProvider.OTHER ? rawProvider : null
                )));
            }
        }
        if (areaColumn != null) {
            String rawArea = view.text(rowNumber, areaColumn);
            if (!rawArea.isEmpty()) {
                GeneralArea detected = area(rawArea);
                entries.add(new RankedPart(5, areaColumn, new PathPart(
                        rawArea,
                        GeneralCategoryNodeType.AREA,
                        detected == null ? GeneralArea.OTHER : detected,
                        null,
                        null
                )));
            }
        }

        Set<Integer> explicitCategoryColumns = new HashSet<>();
        for (String key : List.of(
                "general_category", "category", "category_level",
                "category_level_1", "category_level_2", "category_level_3"
        )) {
            Integer column = header.get(key);
            if (column != null) explicitCategoryColumns.add(column);
        }
        Set<Integer> ignoredColumns = new HashSet<>();
        if (classificationColumn != null) ignoredColumns.add(classificationColumn);
        if (providerColumn != null) ignoredColumns.add(providerColumn);
        if (areaColumn != null) ignoredColumns.add(areaColumn);

        header.sourceHeaders().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int column = entry.getKey();
                    if (ignoredColumns.contains(column)) return;
                    String compactHeader = ExcelText.compact(entry.getValue());
                    boolean taxonomyHeader = explicitCategoryColumns.contains(column)
                            || CATEGORY_HEADER.matcher(compactHeader).find();
                    if (!taxonomyHeader) return;
                    String value = view.text(rowNumber, column);
                    if (value.isEmpty()) return;
                    if (Objects.equals(column, header.get("category_level"))
                            && CATEGORY_LEVEL_LABEL.matcher(ExcelText.compact(value)).matches()) {
                        return;
                    }
                    int rank = taxonomyHeaderRank(compactHeader);
                    List<String> parts = splitTaxonomyValue(value);
                    for (int offset = 0; offset < parts.size(); offset++) {
                        entries.add(new RankedPart(rank + offset, column, new PathPart(
                                parts.get(offset),
                                GeneralCategoryNodeType.CATEGORY,
                                null,
                                null,
                                null
                        )));
                    }
                });

        entries.sort(Comparator.comparingInt(RankedPart::rank).thenComparingInt(RankedPart::column));
        return deduplicatePathParts(entries.stream().map(RankedPart::part).toList());
    }

    private static int taxonomyHeaderRank(String compactHeader) {
        if (compactHeader.contains("대분류") || compactHeader.contains("1차분류")) return 10;
        if (compactHeader.contains("중분류") || compactHeader.contains("2차분류")) return 20;
        if (containsAny(compactHeader, "소분류", "세부분류", "3차분류")) return 30;
        return 40;
    }

    private static List<String> splitTaxonomyValue(String value) {
        return Arrays.stream(CATEGORY_PATH_SEPARATOR.split(value))
                .map(ExcelText::normalize)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static List<PathPart> deduplicatePathParts(Collection<PathPart> parts) {
        List<PathPart> result = new ArrayList<>();
        Set<PathIdentity> seen = new LinkedHashSet<>();
        for (PathPart part : parts) {
            PathIdentity key = new PathIdentity(part.nodeType(), ExcelText.compact(part.name()));
            if (seen.add(key)) result.add(part);
        }
        return result;
    }

    private static void registerCategoryNode(
            Map<String, GeneralCategoryNodeRequest> nodes,
            GeneralCategoryNodeType nodeType,
            ParseState state,
            String sheetName,
            int rowNumber
    ) {
        String name = state.classificationName == null
                ? classificationName(state.classification) : state.classificationName;
        ensurePathNodes(
                nodes,
                state.classification,
                name,
                nodeType == GeneralCategoryNodeType.CLASSIFICATION ? List.of() : state.headingParts,
                sheetName,
                rowNumber
        );
    }

    private static void ensureContextNodes(
            Map<String, GeneralCategoryNodeRequest> nodes,
            GeneralEducationContextRequest context,
            List<PathPart> parts,
            String sheetName,
            int rowNumber
    ) {
        if (context == null) return;
        ensurePathNodes(
                nodes,
                context.classification(),
                context.classificationName() == null
                        ? classificationName(context.classification())
                        : context.classificationName(),
                parts,
                sheetName,
                rowNumber
        );
    }

    private static void ensurePathNodes(
            Map<String, GeneralCategoryNodeRequest> nodes,
            GeneralClassification classification,
            String classificationName,
            List<PathPart> parts,
            String sheetName,
            int rowNumber
    ) {
        String classificationCode = classification == GeneralClassification.UNKNOWN
                ? "OTHER_" + ExcelText.compact(classificationName).toUpperCase()
                : classification.name();
        String parentKey = "CLASSIFICATION:" + classificationCode;
        nodes.putIfAbsent(parentKey, new GeneralCategoryNodeRequest(
                parentKey,
                GeneralCategoryNodeType.CLASSIFICATION,
                classificationCode,
                classificationName,
                null,
                classification,
                classificationName,
                null,
                null,
                null,
                List.of(classificationName),
                sheetName,
                rowNumber,
                nodes.size() + 1
        ));

        List<String> sourcePath = new ArrayList<>(List.of(classificationName));
        for (PathPart part : parts) {
            sourcePath.add(part.name());
            DeliveryProvider provider = null;
            String code;
            if (part.nodeType() == GeneralCategoryNodeType.PROVIDER) {
                provider = part.provider() == null ? DeliveryProvider.OTHER : part.provider();
                code = provider == DeliveryProvider.OTHER
                        ? "OTHER_" + ExcelText.compact(part.name()).toUpperCase()
                        : provider.name();
            } else {
                code = categoryCode(classification, part.name(), part.area());
            }
            String keyComponent = code == null
                    ? ExcelText.compact(part.name()).toUpperCase() : code;
            String nodeKey = parentKey + "/" + part.nodeType().name() + ":" + keyComponent;
            if (!nodes.containsKey(nodeKey)) {
                nodes.put(nodeKey, new GeneralCategoryNodeRequest(
                        nodeKey,
                        part.nodeType(),
                        code,
                        part.name(),
                        parentKey,
                        classification,
                        classificationName,
                        part.area(),
                        provider,
                        part.providerName(),
                        List.copyOf(sourcePath),
                        sheetName,
                        rowNumber,
                        nodes.size() + 1
                ));
            }
            parentKey = nodeKey;
        }
    }

    private static GeneralEducationContextRequest context(
            GeneralClassification classification,
            String classificationName,
            String categoryName,
            GeneralArea area,
            DeliveryProvider provider,
            String providerName,
            List<PathPart> parts
    ) {
        List<String> path = new ArrayList<>(List.of(classificationName));
        parts.forEach(part -> path.add(part.name()));
        return new GeneralEducationContextRequest(
                classification,
                classificationName,
                categoryCode(classification, categoryName, area),
                categoryName,
                area,
                provider,
                providerName,
                List.copyOf(path)
        );
    }

    private static String categoryCode(
            GeneralClassification classification,
            String categoryName,
            GeneralArea area
    ) {
        if (classification == GeneralClassification.ELECTIVE) {
            if (area == GeneralArea.EXPLORATION) return "AREA_1";
            if (area == GeneralArea.COEXISTENCE) return "AREA_2";
            if (area == GeneralArea.INITIATIVE) return "AREA_3";
            String compact = ExcelText.compact(categoryName == null ? "" : categoryName).toUpperCase();
            return compact.isEmpty() ? "GENERAL_ELECTIVE" : "AREA_OTHER_" + compact;
        }
        if (classification == GeneralClassification.UNKNOWN) {
            String compact = ExcelText.compact(categoryName == null ? "" : categoryName).toUpperCase();
            return compact.isEmpty() ? "GENERAL_OTHER" : "GENERAL_OTHER_" + compact;
        }
        String compact = ExcelText.compact(categoryName == null ? "" : categoryName);
        for (Map.Entry<String, String> entry : REQUIRED_CATEGORY_CODES.entrySet()) {
            if (compact.contains(entry.getKey())) return entry.getValue();
        }
        return compact.isEmpty() ? null : "REQUIRED_" + compact.toUpperCase();
    }

    private static ParseState stateFromSheetName(String sheetName) {
        ParseState state = new ParseState();
        String name = ExcelText.normalize(sheetName);
        String compact = ExcelText.compact(name);
        if (compact.isEmpty()) return state;

        if (compact.contains("교양필수") || Set.of("필수", "필수교양").contains(compact)) {
            state.classification = GeneralClassification.REQUIRED;
            state.classificationName = "교양필수";
        } else if (compact.contains("교양선택") || Set.of("선택", "선택교양").contains(compact)) {
            state.classification = GeneralClassification.ELECTIVE;
            state.classificationName = "교양선택";
        }

        DeliveryProvider provider = providerMarker(name);
        if (provider != null) {
            ensureElectiveClassification(state);
            state.provider = provider;
            state.providerHeading = name;
            state.headingParts = new ArrayList<>(List.of(new PathPart(
                    name,
                    GeneralCategoryNodeType.PROVIDER,
                    null,
                    provider,
                    provider == DeliveryProvider.OTHER ? name : null
            )));
            return state;
        }

        GeneralArea area = area(name);
        if (area != null) {
            ensureElectiveClassification(state);
            state.categoryName = name;
            state.area = area;
            state.headingParts = new ArrayList<>(List.of(new PathPart(
                    name, GeneralCategoryNodeType.AREA, area, null, null
            )));
            return state;
        }

        Set<String> genericNames = Set.of(
                "sheet", "sheet1", "시트", "시트1", "강좌", "강의", "교양",
                "교양강좌", "교양시간표", "교양교육과정"
        );
        if (state.classificationName == null
                && !genericNames.contains(compact)
                && (compact.endsWith("교양") || compact.contains("교양교육"))) {
            state.classification = GeneralClassification.UNKNOWN;
            state.classificationName = name;
        }
        return state;
    }

    private static GeneralClassification classificationMarker(String compact) {
        if (CLASSIFICATION_REQUIRED.matcher(compact).matches()) return GeneralClassification.REQUIRED;
        if (CLASSIFICATION_ELECTIVE.matcher(compact).matches()) return GeneralClassification.ELECTIVE;
        return null;
    }

    private static DeliveryProvider providerMarker(String value) {
        String compact = ExcelText.compact(value);
        if (compact.contains("본교사이버") || compact.contains("hsucyber")) {
            return DeliveryProvider.HSU_CYBER;
        }
        if (OCU.matcher(ExcelText.normalize(value)).find()) return DeliveryProvider.OCU;
        if (compact.contains("대전충남")) return DeliveryProvider.CHUNGNAM_ELEARNING;
        if (compact.contains("sdu") || compact.contains("서울디지털")) return DeliveryProvider.SDU;
        if (containsAny(compact,
                "사이버강좌", "이러닝강좌", "컨소시엄강좌", "온라인강좌", "원격강좌",
                "mooc", "프리즘", "학습망", "학습플랫폼", "교육플랫폼", "lms")) {
            return DeliveryProvider.OTHER;
        }
        return null;
    }

    private static GeneralArea area(String value) {
        String compact = ExcelText.compact(value);
        if (compact.isEmpty()) return null;
        if (compact.startsWith("1영역") || compact.contains("탐구")) return GeneralArea.EXPLORATION;
        if (compact.startsWith("2영역") || compact.contains("상생")) return GeneralArea.COEXISTENCE;
        if (compact.startsWith("3영역") || compact.contains("진취")) return GeneralArea.INITIATIVE;
        if (compact.matches("^\\d+영역.*") || compact.contains("일반선택")
                || compact.contains("영역") || compact.endsWith("역량") || compact.endsWith("소양")) {
            return GeneralArea.OTHER;
        }
        return null;
    }

    private static ProviderResult effectiveProvider(
            DeliveryProvider provider,
            String scheduleText,
            String classroomText
    ) {
        if (provider != DeliveryProvider.ON_CAMPUS) return new ProviderResult(provider, null);
        String room = ExcelText.compact(classroomText);
        String schedule = ExcelText.compact(scheduleText);
        if (room.equals("eclass")) return new ProviderResult(DeliveryProvider.E_CLASS, null);
        if (room.equals("cyber") || schedule.equals("cyber")) {
            return new ProviderResult(DeliveryProvider.HSU_CYBER, null);
        }
        for (String original : List.of(scheduleText, classroomText)) {
            String compact = ExcelText.compact(original);
            if (compact.contains("프리즘시스템") || compact.contains("프리즘시스탬")) {
                return new ProviderResult(DeliveryProvider.OTHER, ExcelText.normalize(original));
            }
        }
        return new ProviderResult(provider, null);
    }

    private static ClassificationResult rowClassification(
            SheetView view,
            int rowNumber,
            HeaderMap header
    ) {
        Integer column = header.get("classification");
        if (column == null) return new ClassificationResult(null, null);
        String raw = view.text(rowNumber, column);
        String compact = ExcelText.compact(raw);
        if (compact.contains("필수")) return new ClassificationResult(GeneralClassification.REQUIRED, raw);
        if (compact.contains("선택")) return new ClassificationResult(GeneralClassification.ELECTIVE, raw);
        return raw.isEmpty()
                ? new ClassificationResult(null, null)
                : new ClassificationResult(GeneralClassification.UNKNOWN, raw);
    }

    private static String read(
            SheetView view,
            int rowNumber,
            HeaderMap header,
            String key,
            boolean identifier
    ) {
        Integer column = header.get(key);
        return column == null ? "" : view.text(rowNumber, column, identifier);
    }

    private static String normalizeCourseCode(String value) {
        return ExcelText.normalize(value);
    }

    private static boolean hasDirectIdentityValue(
            SheetView view,
            int rowNumber,
            HeaderMap header
    ) {
        if (header == null || rowNumber <= header.rowNumber()) {
            return false;
        }
        Integer codeColumn = header.get("course_code");
        Integer nameColumn = header.get("course_name");
        return (codeColumn != null
                && !view.isInMultiColumnMergedRegion(rowNumber, codeColumn)
                && !view.text(rowNumber, codeColumn, true, false).isEmpty())
                || (nameColumn != null
                && !view.isInMultiColumnMergedRegion(rowNumber, nameColumn)
                && !view.text(rowNumber, nameColumn, false, false).isEmpty());
    }

    private static String ambiguousMergedIdentityField(
            SheetView view,
            int rowNumber,
            HeaderMap header
    ) {
        if (header == null || rowNumber <= header.rowNumber()) {
            return null;
        }
        int minColumn = header.columns().values().stream().mapToInt(Integer::intValue).min().orElse(1);
        int maxColumn = header.columns().values().stream().mapToInt(Integer::intValue).max().orElse(1);
        int headerWidth = Math.max(1, maxColumn - minColumn + 1);
        int broadHeadingThreshold = Math.max(3, (headerWidth * 3 + 3) / 4);

        for (String field : List.of("course_code", "course_name")) {
            Integer column = header.get(field);
            if (column == null) {
                continue;
            }
            String directValue = view.text(rowNumber, column, false, false);
            if (directValue.isEmpty() || knownHeadingValue(directValue)) {
                continue;
            }
            if ("course_code".equals(field)
                    && !COURSE_CODE.matcher(normalizeCourseCode(directValue)).matches()) {
                continue;
            }
            int mergedSpan = view.mergedColumnSpan(rowNumber, column);
            if (mergedSpan > 1 && mergedSpan < broadHeadingThreshold) {
                return "course_code".equals(field) ? "courseCode" : "courseName";
            }
        }
        return null;
    }

    private static boolean knownHeadingValue(String value) {
        String compact = ExcelText.compact(value);
        return isTitle(value)
                || classificationMarker(compact) != null
                || providerMarker(value) != null
                || isStandaloneNote(value)
                || isPlainGuide(value)
                || area(value) != null
                || TOP_LEVEL_MARKER.matcher(value).find();
    }

    private static boolean looksLikeDataRow(SheetView view, int row, HeaderMap header) {
        if (!read(view, row, header, "course_name", false).isEmpty()
                || !read(view, row, header, "course_code", true).isEmpty()) {
            return true;
        }
        int signals = 0;
        for (String field : List.of(
                "grade", "section_no", "credit", "class_hours", "instructor", "schedule", "classroom"
        )) {
            if (!read(view, row, header, field, false).isEmpty()) signals++;
        }
        return signals >= 3;
    }

    private static Double number(String value) {
        return ExcelValueParser.parseNumber(value);
    }

    private static GradeResult grade(String value) {
        ExcelValueParser.GradeValue targetGrade = ExcelValueParser.parseTargetGrade(value);
        List<Integer> allowedGrades = targetGrade.commonGrade()
                ? List.of(1, 2, 3, 4)
                : ExcelValueParser.parseAllowedGrades(value, 4);
        return new GradeResult(
                targetGrade.targetGrade(),
                targetGrade.commonGrade(),
                allowedGrades
        );
    }

    private static List<Integer> constrainAllowedGrades(List<Integer> base, List<String> restrictions) {
        Set<Integer> allowed = new LinkedHashSet<>(base);
        boolean constrained = !base.isEmpty();
        for (String restriction : restrictions) {
            Set<Integer> constraint = gradeConstraint(restriction);
            if (constraint == null) continue;
            if (constrained) allowed.retainAll(constraint);
            else {
                allowed = new LinkedHashSet<>(constraint);
                constrained = true;
            }
        }
        if (!constrained) return List.of();
        return allowed.stream().filter(item -> item >= 1 && item <= 4).sorted().toList();
    }

    private static Set<Integer> gradeConstraint(String value) {
        String text = ExcelText.normalize(value);
        String compact = ExcelText.compact(text);
        if (compact.isEmpty()) return null;
        if (Pattern.compile("1학년.*(?:불가능|제외|금지)").matcher(text).find()) {
            return Set.of(2, 3, 4);
        }
        Matcher range = GRADE_RANGE.matcher(text);
        if (range.find()) {
            int first = Integer.parseInt(range.group(1));
            int second = Integer.parseInt(range.group(2));
            Set<Integer> values = new LinkedHashSet<>();
            for (int grade = Math.min(first, second); grade <= Math.max(first, second); grade++) {
                values.add(grade);
            }
            return values;
        }
        Matcher only = GRADE_ONLY.matcher(text);
        if (only.find()) return Set.of(Integer.parseInt(only.group(1)));
        if (compact.contains("전학년") || compact.contains("전체학년")) return Set.of(1, 2, 3, 4);
        if (Pattern.compile("1학년.*(?:가능|허용)").matcher(text).find()) return Set.of(1, 2, 3, 4);
        return null;
    }

    private static String providerRestriction(String providerHeading) {
        if (providerHeading == null || RESTRICTION_WORDS.stream().noneMatch(providerHeading::contains)) {
            return "";
        }
        Matcher matcher = PROVIDER_RESTRICTION.matcher(providerHeading);
        return ExcelText.normalize(matcher.find() ? matcher.group(1) : providerHeading);
    }

    private static boolean looksLikeRestriction(String value) {
        String normalized = ExcelText.normalize(value);
        return RESTRICTION_WORDS.stream().anyMatch(normalized::contains);
    }

    private static boolean truthy(String value) {
        return Set.of("y", "yes", "true", "1", "해당", "팀티칭", "o")
                .contains(ExcelText.compact(value));
    }

    private static List<String> eligibleDepartments(String value) {
        if (value == null || value.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (String rawPart : splitTopLevelCommas(value)) {
            String part = ExcelText.normalize(rawPart);
            if (part.isEmpty()) continue;
            List<String> candidateParts = new ArrayList<>(pending);
            candidateParts.add(part);
            String candidate = String.join(",", candidateParts);
            if (isAcademicUnit(candidate)) {
                candidate = candidate.replaceFirst("\\s*\\(태안\\)\\s*$", "").trim();
                if (!result.contains(candidate)) result.add(candidate);
                pending.clear();
            } else if (isAcademicUnit(part)) {
                if (!result.contains(part)) result.add(part);
                pending.clear();
            } else {
                pending.add(part);
            }
        }
        return result;
    }

    private static List<String> splitTopLevelCommas(String value) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ("([<{".indexOf(character) >= 0) depth++;
            else if (")]}>".indexOf(character) >= 0 && depth > 0) depth--;
            else if ((character == ',' || character == '，') && depth == 0) {
                parts.add(value.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }

    private static boolean isAcademicUnit(String value) {
        String normalized = ExcelText.normalize(value);
        return UNIT_SUFFIX.matcher(normalized).find()
                || normalized.startsWith("전공자율선택(")
                || normalized.startsWith("자유전공");
    }

    private static void applyBlockNote(String value, ParseState state, CourseParserOutput output) {
        String note = ExcelText.normalize(value).replaceFirst("^[*※\\s]+", "").trim();
        Set<Integer> constraint = gradeConstraint(note);
        for (int index : state.blockLectureIndices) {
            LectureRequest lecture = output.lectures().get(index);
            String updatedNote = emptyToNull(joinUnique(List.of(
                    nullToEmpty(lecture.note()), note
            )));
            String updatedEligibility = lecture.eligibilityNote();
            if (looksLikeRestriction(note)) {
                updatedEligibility = emptyToNull(joinUnique(List.of(
                        nullToEmpty(lecture.eligibilityNote()), note
                )));
            }
            List<Integer> updatedGrades = constraint == null
                    ? lecture.allowedGrades()
                    : constrainAllowedGrades(lecture.allowedGrades(), List.of(note));
            output.lectures().set(index, copyLecture(
                    lecture,
                    updatedNote,
                    updatedEligibility,
                    updatedGrades
            ));
        }
    }

    private static LectureRequest copyLecture(
            LectureRequest lecture,
            String note,
            String eligibilityNote,
            List<Integer> allowedGrades
    ) {
        return new LectureRequest(
                lecture.sourceSheet(), lecture.sourceRow(), lecture.curriculumType(),
                lecture.academicUnit(), lecture.generalEducation(), lecture.courseCode(),
                lecture.courseName(), lecture.sectionNo(), lecture.credit(), lecture.classHours(),
                lecture.instructorName(), lecture.targetGrade(), lecture.commonGrade(),
                List.copyOf(allowedGrades), lecture.eligibleDepartmentNames(), lecture.teamTeaching(),
                note, eligibilityNote, lecture.scheduleText(), lecture.classroomText(),
                lecture.schedules(), lecture.sourceCells()
        );
    }

    private static String joinUnique(Collection<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = ExcelText.normalize(value);
            if (!normalized.isEmpty() && !result.contains(normalized)) result.add(normalized);
        }
        return String.join(" | ", result);
    }

    private static boolean isStandaloneNote(String value) {
        String stripped = ExcelText.normalize(value).stripLeading();
        return stripped.startsWith("*") || stripped.startsWith("※");
    }

    private static boolean isPlainGuide(String value) {
        String compact = ExcelText.compact(value);
        return containsAny(compact, "수강신청안내", "유의사항", "주의사항");
    }

    private static boolean hasChildHeading(SheetView view, int rowNumber) {
        int last = Math.min(view.maxRow(), rowNumber + 20);
        for (int candidate = rowNumber + 1; candidate <= last; candidate++) {
            if (HeaderDetector.detectHeader(view, candidate) != null) return false;
            List<String> values = view.meaningfulValues(candidate, false);
            if (values.isEmpty()) continue;
            if (values.size() != 1) return false;
            String value = values.get(0);
            if (isTitle(value) || isStandaloneNote(value)) continue;
            return true;
        }
        return false;
    }

    private static boolean isTitle(String value) {
        String compact = ExcelText.compact(value);
        return ACADEMIC_YEAR_TITLE.matcher(compact).find() || compact.contains("교양교육과정");
    }

    private static boolean isFirstHalfOfSupportedHeader(SheetView view, int rowNumber) {
        if (rowNumber >= view.maxRow()) return false;
        int last = Math.min(view.maxRow(), rowNumber + HeaderDetector.MAX_HEADER_ROWS - 1);
        for (int candidate = rowNumber + 1; candidate <= last; candidate++) {
            HeaderMap next = HeaderDetector.detectHeader(view, candidate);
            if (next != null && next.startRow() <= rowNumber) return true;
        }
        return false;
    }

    private static String classificationName(GeneralClassification classification) {
        if (classification == GeneralClassification.REQUIRED) return "교양필수";
        if (classification == GeneralClassification.ELECTIVE) return "교양선택";
        return "미분류";
    }

    private static String cleanMarkerName(String value) {
        return ExcelText.normalize(value).replaceFirst("^[▶▷■●◆◇▣]+", "").trim();
    }

    private static PathPart findLastPart(
            List<PathPart> parts,
            Set<GeneralCategoryNodeType> types
    ) {
        for (int index = parts.size() - 1; index >= 0; index--) {
            if (types.contains(parts.get(index).nodeType())) return parts.get(index);
        }
        return null;
    }

    private static ParseIssueRequest issue(
            SheetView view,
            int rowNumber,
            IssueSeverity severity,
            String code,
            String message,
            String field,
            String rawValue
    ) {
        return new ParseIssueRequest(
                severity, code, message, view.name(), rowNumber, field, rawValue
        );
    }

    private static GeneralCategoryNodeRequest copyNodeWithSortOrder(
            GeneralCategoryNodeRequest node,
            int sortOrder
    ) {
        return new GeneralCategoryNodeRequest(
                node.nodeKey(), node.nodeType(), node.code(), node.name(), node.parentKey(),
                node.classification(), node.classificationName(), node.area(),
                node.deliveryProvider(), node.deliveryProviderName(), node.sourcePath(),
                node.sourceSheet(), node.sourceRow(), sortOrder
        );
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String firstNonNull(String... values) {
        for (String value : values) if (value != null) return value;
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private record PathPart(
            String name,
            GeneralCategoryNodeType nodeType,
            GeneralArea area,
            DeliveryProvider provider,
            String providerName
    ) {
    }

    private record RankedPart(int rank, int column, PathPart part) {
    }

    private record RowIdentity(String sheet, int row) {
    }

    private record PathIdentity(GeneralCategoryNodeType type, String compactName) {
    }

    private record MarkerResult(boolean consumed, GeneralCategoryNodeType nodeType) {
        static MarkerResult consumed(GeneralCategoryNodeType nodeType) {
            return new MarkerResult(true, nodeType);
        }

        static MarkerResult notConsumed() {
            return new MarkerResult(false, null);
        }
    }

    private record LectureResult(
            LectureRequest lecture,
            List<ParseIssueRequest> issues,
            List<PathPart> contextParts
    ) {
    }

    private record ProviderResult(DeliveryProvider provider, String name) {
    }

    private record ClassificationResult(GeneralClassification classification, String name) {
    }

    private record GradeResult(Integer target, boolean common, List<Integer> allowed) {
    }

    private static final class ParseState {
        private GeneralClassification classification = GeneralClassification.UNKNOWN;
        private String classificationName;
        private String categoryName;
        private GeneralArea area;
        private DeliveryProvider provider = DeliveryProvider.ON_CAMPUS;
        private String providerHeading;
        private HeaderMap header;
        private final List<Integer> blockLectureIndices = new ArrayList<>();
        private List<PathPart> headingParts = new ArrayList<>();
    }
}
