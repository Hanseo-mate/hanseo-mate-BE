package hsu.hanseomate.domain.courseimport.service;

import tools.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.course.entity.AcademicUnit;
import hsu.hanseomate.domain.course.entity.Classroom;
import hsu.hanseomate.domain.course.entity.Course;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.entity.CourseSourceCell;
import hsu.hanseomate.domain.course.entity.OfferingAllowedGrade;
import hsu.hanseomate.domain.course.entity.OfferingEligibleDepartment;
import hsu.hanseomate.domain.course.entity.OfferingGeneralEducation;
import hsu.hanseomate.domain.course.entity.Semester;
import hsu.hanseomate.domain.course.entity.SemesterAcademicUnit;
import hsu.hanseomate.domain.course.entity.SemesterGeneralCategoryNode;
import hsu.hanseomate.domain.course.repository.AcademicUnitRepository;
import hsu.hanseomate.domain.course.repository.ClassroomRepository;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseRepository;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.course.repository.CourseSourceCellRepository;
import hsu.hanseomate.domain.course.repository.OfferingAllowedGradeRepository;
import hsu.hanseomate.domain.course.repository.OfferingEligibleDepartmentRepository;
import hsu.hanseomate.domain.course.repository.OfferingGeneralEducationRepository;
import hsu.hanseomate.domain.course.repository.SemesterAcademicUnitRepository;
import hsu.hanseomate.domain.course.repository.SemesterGeneralCategoryNodeRepository;
import hsu.hanseomate.domain.course.repository.SemesterRepository;
import hsu.hanseomate.domain.courseimport.dto.AcademicUnitRequest;
import hsu.hanseomate.domain.courseimport.dto.ClassroomRequest;
import hsu.hanseomate.domain.courseimport.dto.CourseImportResponse;
import hsu.hanseomate.domain.courseimport.dto.CourseImportIssueResponse;
import hsu.hanseomate.domain.courseimport.dto.GeneralCategoryNodeRequest;
import hsu.hanseomate.domain.courseimport.dto.GeneralEducationContextRequest;
import hsu.hanseomate.domain.courseimport.dto.LectureRequest;
import hsu.hanseomate.domain.courseimport.dto.ParseIssueRequest;
import hsu.hanseomate.domain.courseimport.dto.SourceCellRequest;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import hsu.hanseomate.domain.courseimport.entity.CourseImportIssue;
import hsu.hanseomate.domain.courseimport.repository.CourseImportHistoryRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseImportService {

    private final CourseImportContractValidator validator;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final SemesterRepository semesterRepository;
    private final AcademicUnitRepository academicUnitRepository;
    private final CourseRepository courseRepository;
    private final ClassroomRepository classroomRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final CourseSourceCellRepository courseSourceCellRepository;
    private final OfferingGeneralEducationRepository offeringGeneralEducationRepository;
    private final OfferingAllowedGradeRepository offeringAllowedGradeRepository;
    private final OfferingEligibleDepartmentRepository offeringEligibleDepartmentRepository;
    private final SemesterAcademicUnitRepository semesterAcademicUnitRepository;
    private final SemesterGeneralCategoryNodeRepository semesterGeneralCategoryNodeRepository;
    private final CourseImportHistoryRepository courseImportHistoryRepository;

    @Transactional
    public CourseImportResponse importCourses(TimetableParseResultRequest request) {
        String idempotencyKey = idempotencyKey(request);

        CourseImportHistory reusedImportId = courseImportHistoryRepository
                .findByImportId(request.importId())
                .orElse(null);
        if (reusedImportId != null) {
            if (idempotencyKey.equals(reusedImportId.getSuccessfulDedupKey())) {
                return new CourseImportResponse(
                        request.importId(), StorageStatus.DUPLICATE, false,
                        reusedImportId.getOfferingCount(), "이미 반영된 파일입니다.", List.of()
                );
            }
            throw new hsu.hanseomate.domain.courseimport.exception.CourseImportContractException(
                    "이미 사용된 importId입니다."
            );
        }

        List<CourseImportIssueResponse> reviewIssues = validator.reviewIssues(request);
        if (!reviewIssues.isEmpty()) {
            persistReviewHistory(request, idempotencyKey, reviewIssues);
            return new CourseImportResponse(
                    request.importId(),
                    StorageStatus.REVIEW_REQUIRED,
                    false,
                    0,
                    "검토가 필요한 항목이 %d개 있어 저장하지 않았습니다."
                            .formatted(reviewIssues.size()),
                    reviewIssues
            );
        }

        Semester semester = findOrCreateLockedSemester(request.academicYear(), request.semester());
        List<CourseImportHistory> currentHistories = courseOfferingRepository
                .findImportHistoriesByScope(semester.getId(), request.curriculumType());
        if (currentHistories.size() > 1) {
            throw new IllegalStateException("현재 강좌 스냅샷에 서로 다른 수입 이력이 연결되어 있습니다.");
        }
        CourseImportHistory currentHistory = currentHistories.stream().findFirst().orElse(null);
        if (currentHistory != null
                && idempotencyKey.equals(currentHistory.getIdempotencyKey())) {
            return new CourseImportResponse(
                    request.importId(),
                    StorageStatus.DUPLICATE,
                    false,
                    currentHistory.getOfferingCount(),
                    "이미 반영된 파일입니다.",
                    List.of()
            );
        }

        CourseImportHistory previousSameFile = courseImportHistoryRepository
                .findBySuccessfulDedupKey(idempotencyKey)
                .orElse(null);
        if (previousSameFile != null && previousSameFile != currentHistory) {
            previousSameFile.markSuperseded();
        }
        if (currentHistory != null) {
            currentHistory.markSuperseded();
        }
        entityManager.flush();

        Map<String, AcademicUnit> academicUnits = resolveAcademicUnits(request);
        Map<String, Course> courses = resolveCourses(request);
        Map<String, Classroom> classrooms = resolveClassrooms(request);

        replaceScope(semester, request.curriculumType());

        String rawPayload = serialize(request);
        CourseImportHistory history = CourseImportHistory.stored(
                request.importId(),
                idempotencyKey,
                request.fileName(),
                request.fileSha256(),
                request.schemaVersion(),
                request.parserVersion(),
                request.academicYear(),
                request.semester(),
                request.curriculumType(),
                request.displayName(),
                BigDecimal.valueOf(request.confidence()),
                request.lectures().size(),
                rawPayload
        );
        entityManager.persist(history);

        persistSemesterAcademicUnits(semester, request.curriculumType(), academicUnits.values());
        persistGeneralCategoryNodes(semester, request, history);

        List<CourseOffering> offerings = persistOfferings(
                semester, request, history, academicUnits, courses
        );
        persistOfferingDetails(request.lectures(), offerings, classrooms);
        persistImportIssues(request.issues(), history);

        entityManager.flush();
        return new CourseImportResponse(
                request.importId(),
                StorageStatus.STORED,
                true,
                request.lectures().size(),
                successMessage(request),
                List.of()
        );
    }

    private void persistReviewHistory(
            TimetableParseResultRequest request,
            String idempotencyKey,
            List<CourseImportIssueResponse> reviewIssues
    ) {
        CourseImportHistory history = CourseImportHistory.reviewRequired(
                request.importId(),
                idempotencyKey,
                request.fileName(),
                request.fileSha256(),
                request.schemaVersion(),
                request.parserVersion(),
                request.academicYear(),
                request.semester(),
                request.curriculumType(),
                request.displayName(),
                BigDecimal.valueOf(request.confidence()),
                serialize(request)
        );
        entityManager.persist(history);
        persistImportIssueResponses(reviewIssues, history);
        entityManager.flush();
    }

    private Semester findOrCreateLockedSemester(int academicYear, int term) {
        Semester existing = semesterRepository.findForUpdate(academicYear, term).orElse(null);
        if (existing != null) {
            return existing;
        }
        Semester semester = Semester.create(academicYear, term);
        entityManager.persist(semester);
        entityManager.flush();
        return semester;
    }

    private Map<String, AcademicUnit> resolveAcademicUnits(TimetableParseResultRequest request) {
        LinkedHashMap<String, AcademicUnitRequest> requested = new LinkedHashMap<>();
        request.academicUnits().forEach(unit -> requested.put(academicUnitKey(unit), unit));
        request.lectures().stream()
                .map(LectureRequest::academicUnit)
                .filter(Objects::nonNull)
                .forEach(unit -> requested.put(academicUnitKey(unit), unit));

        Map<String, AcademicUnit> result = academicUnitRepository
                .findAllByMasterKeyIn(requested.keySet())
                .stream()
                .collect(Collectors.toMap(AcademicUnit::getMasterKey, Function.identity()));
        requested.forEach((key, unit) -> {
            if (!result.containsKey(key)) {
                AcademicUnit created = AcademicUnit.create(
                        key, unit.originalName(), unit.departmentName(), unit.majorName()
                );
                entityManager.persist(created);
                result.put(key, created);
            }
        });
        return result;
    }

    private Map<String, Course> resolveCourses(TimetableParseResultRequest request) {
        LinkedHashMap<String, LectureRequest> requested = new LinkedHashMap<>();
        for (LectureRequest lecture : request.lectures()) {
            requested.put(courseKey(request.importId(), lecture), lecture);
        }
        Map<String, Course> result = courseRepository.findAllByMasterKeyIn(requested.keySet())
                .stream()
                .collect(Collectors.toMap(Course::getMasterKey, Function.identity()));
        requested.forEach((key, lecture) -> {
            Course existing = result.get(key);
            if (existing == null) {
                Course created = Course.create(key, lecture.courseCode(), lecture.courseName());
                entityManager.persist(created);
                result.put(key, created);
            } else {
                existing.updateName(lecture.courseName());
            }
        });
        return result;
    }

    private Map<String, Classroom> resolveClassrooms(TimetableParseResultRequest request) {
        LinkedHashMap<String, ClassroomRequest> requested = new LinkedHashMap<>();
        request.lectures().stream()
                .flatMap(lecture -> lecture.schedules().stream())
                .map(schedule -> schedule.classroom())
                .filter(Objects::nonNull)
                .forEach(classroom -> requested.put(classroomKey(classroom), classroom));

        Map<String, Classroom> result = classroomRepository.findAllByMasterKeyIn(requested.keySet())
                .stream()
                .collect(Collectors.toMap(Classroom::getMasterKey, Function.identity()));
        requested.forEach((key, classroom) -> {
            if (!result.containsKey(key)) {
                Classroom created = Classroom.create(
                        key,
                        classroom.campusCode(),
                        classroom.buildingName(),
                        classroom.roomNumber(),
                        classroom.originalValue()
                );
                entityManager.persist(created);
                result.put(key, created);
            }
        });
        return result;
    }

    private void replaceScope(Semester semester, CurriculumType curriculumType) {
        List<UUID> offeringIds = courseOfferingRepository.findIdsByScope(semester.getId(), curriculumType);
        if (!offeringIds.isEmpty()) {
            courseScheduleRepository.deleteByOfferingIds(offeringIds);
            courseSourceCellRepository.deleteByOfferingIds(offeringIds);
            offeringAllowedGradeRepository.deleteByOfferingIds(offeringIds);
            offeringEligibleDepartmentRepository.deleteByOfferingIds(offeringIds);
            offeringGeneralEducationRepository.deleteByOfferingIds(offeringIds);
            courseOfferingRepository.deleteAllByIdIn(offeringIds);
        }
        semesterAcademicUnitRepository.deleteByScope(semester.getId(), curriculumType);
        semesterGeneralCategoryNodeRepository.deleteByScope(semester.getId(), curriculumType);
        entityManager.flush();
    }

    private void persistSemesterAcademicUnits(
            Semester semester,
            CurriculumType curriculumType,
            Collection<AcademicUnit> academicUnits
    ) {
        academicUnits.stream()
                .map(unit -> SemesterAcademicUnit.create(semester, unit, curriculumType))
                .forEach(entityManager::persist);
    }

    private void persistGeneralCategoryNodes(
            Semester semester,
            TimetableParseResultRequest request,
            CourseImportHistory history
    ) {
        for (GeneralCategoryNodeRequest node : request.generalCategoryNodes()) {
            SemesterGeneralCategoryNode entity = SemesterGeneralCategoryNode.create(
                    semester,
                    request.curriculumType(),
                    node.nodeKey(),
                    node.nodeType(),
                    node.code(),
                    node.name(),
                    node.parentKey(),
                    node.classification(),
                    node.classificationName(),
                    node.area(),
                    node.deliveryProvider(),
                    node.deliveryProviderName(),
                    serialize(node.sourcePath()),
                    node.sourceSheet(),
                    node.sourceRow(),
                    node.sortOrder()
            );
            entityManager.persist(entity);
        }
    }

    private List<CourseOffering> persistOfferings(
            Semester semester,
            TimetableParseResultRequest request,
            CourseImportHistory history,
            Map<String, AcademicUnit> academicUnits,
            Map<String, Course> courses
    ) {
        List<CourseOffering> offerings = new ArrayList<>(request.lectures().size());
        for (LectureRequest lecture : request.lectures()) {
            AcademicUnit academicUnit = lecture.academicUnit() == null
                    ? null
                    : academicUnits.get(academicUnitKey(lecture.academicUnit()));
            CourseOffering offering = CourseOffering.create(
                    semester,
                    courses.get(courseKey(request.importId(), lecture)),
                    academicUnit,
                    history,
                    request.curriculumType(),
                    lecture.sourceSheet(),
                    lecture.sourceRow(),
                    lecture.courseCode(),
                    lecture.courseName(),
                    lecture.sectionNo(),
                    decimal(lecture.credit()),
                    decimal(lecture.classHours()),
                    lecture.instructorName(),
                    lecture.targetGrade(),
                    lecture.commonGrade(),
                    lecture.teamTeaching(),
                    lecture.note(),
                    lecture.eligibilityNote(),
                    lecture.scheduleText(),
                    lecture.classroomText()
            );
            entityManager.persist(offering);
            offerings.add(offering);
        }
        return offerings;
    }

    private void persistOfferingDetails(
            List<LectureRequest> lectures,
            List<CourseOffering> offerings,
            Map<String, Classroom> classrooms
    ) {
        for (int index = 0; index < lectures.size(); index++) {
            LectureRequest lecture = lectures.get(index);
            CourseOffering offering = offerings.get(index);

            if (lecture.generalEducation() != null) {
                persistGeneralEducation(offering, lecture.generalEducation());
            }
            lecture.allowedGrades().stream()
                    .distinct()
                    .map(grade -> OfferingAllowedGrade.create(offering, grade))
                    .forEach(entityManager::persist);
            lecture.eligibleDepartmentNames().stream()
                    .distinct()
                    .map(name -> OfferingEligibleDepartment.create(offering, name))
                    .forEach(entityManager::persist);
            for (SourceCellRequest cell : lecture.sourceCells()) {
                entityManager.persist(CourseSourceCell.create(
                        offering,
                        cell.columnIndex(),
                        cell.headerName(),
                        cell.canonicalField(),
                        cell.value()
                ));
            }
            for (int scheduleIndex = 0; scheduleIndex < lecture.schedules().size(); scheduleIndex++) {
                var schedule = lecture.schedules().get(scheduleIndex);
                Classroom classroom = schedule.classroom() == null
                        ? null
                        : classrooms.get(classroomKey(schedule.classroom()));
                entityManager.persist(CourseSchedule.create(
                        offering, scheduleIndex, schedule.dayOfWeek(), schedule.periods(), classroom
                ));
            }
        }
    }

    private void persistGeneralEducation(
            CourseOffering offering,
            GeneralEducationContextRequest context
    ) {
        entityManager.persist(OfferingGeneralEducation.create(
                offering,
                context.classification(),
                context.classificationName(),
                context.categoryCode(),
                context.categoryName(),
                context.area(),
                context.deliveryProvider(),
                context.deliveryProviderName(),
                serialize(context.sourcePath())
        ));
    }

    private void persistImportIssues(List<ParseIssueRequest> issues, CourseImportHistory history) {
        issues.stream()
                .limit(CourseImportContractValidator.MAX_REVIEW_ISSUES)
                .map(issue -> CourseImportIssue.create(
                        history,
                        issue.severity(),
                        issue.code(),
                        issue.message(),
                        issue.sheetName(),
                        issue.rowNumber(),
                        issue.field(),
                        issue.rawValue()
                ))
                .forEach(entityManager::persist);
    }

    private void persistImportIssueResponses(
            List<CourseImportIssueResponse> issues,
            CourseImportHistory history
    ) {
        issues.stream()
                .map(issue -> CourseImportIssue.create(
                        history,
                        issue.severity(),
                        issue.code(),
                        issue.message(),
                        issue.sheetName(),
                        issue.rowNumber(),
                        issue.field(),
                        issue.rawValue()
                ))
                .forEach(entityManager::persist);
    }

    private String successMessage(TimetableParseResultRequest request) {
        String typeName = request.curriculumType() == CurriculumType.MAJOR ? "전공" : "교양";
        return "%d학년도 %d학기 %s 강좌 저장 완료".formatted(
                request.academicYear(), request.semester(), typeName
        );
    }

    private String idempotencyKey(TimetableParseResultRequest request) {
        return "%d:%d:%s:%s".formatted(
                request.academicYear(),
                request.semester(),
                request.curriculumType(),
                request.fileSha256()
        );
    }

    private String academicUnitKey(AcademicUnitRequest unit) {
        return sha256(String.join("|",
                unit.originalName(), unit.departmentName(), nullable(unit.majorName())));
    }

    private String courseKey(String importId, LectureRequest lecture) {
        if (lecture.courseCode() != null) {
            return sha256("CODE|" + lecture.courseCode());
        }
        if (lecture.courseName() != null) {
            return sha256("NAME|" + lecture.courseName());
        }
        return sha256("IMPORT|%s|%s|%d".formatted(
                importId, lecture.sourceSheet(), lecture.sourceRow()
        ));
    }

    private String classroomKey(ClassroomRequest classroom) {
        return sha256(String.join("|",
                nullable(classroom.campusCode()),
                nullable(classroom.buildingName()),
                nullable(classroom.roomNumber()),
                classroom.originalValue()));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("강좌 원본 JSON을 직렬화할 수 없습니다.", exception);
        }
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String nullable(String value) {
        return value == null ? "" : value;
    }
}
