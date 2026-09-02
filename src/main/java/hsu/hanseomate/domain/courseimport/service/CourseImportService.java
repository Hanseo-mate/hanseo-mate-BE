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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final OfferingAllowedGradeRepository offeringAllowedGradeRepository;
    private final OfferingEligibleDepartmentRepository offeringEligibleDepartmentRepository;
    private final OfferingGeneralEducationRepository offeringGeneralEducationRepository;
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

        List<LectureRequest> lectures = deduplicateLectures(request);
        Map<String, AcademicUnit> academicUnits = resolveAcademicUnits(request, lectures);
        List<ResolvedCourse> courses = resolveCourses(
                request,
                lectures,
                academicUnits
        );
        Map<String, Classroom> classrooms = resolveClassrooms(lectures);

        replaceScopeTaxonomy(semester, request.curriculumType());

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
                lectures.size(),
                rawPayload
        );
        entityManager.persist(history);

        persistSemesterAcademicUnits(semester, request.curriculumType(), academicUnits.values());
        persistGeneralCategoryNodes(semester, request, history);

        List<ResolvedOffering> offerings = syncOfferings(
                semester,
                request,
                history,
                courses
        );
        replaceCourseDetails(courses, classrooms);
        replaceSourceCells(offerings);
        persistImportIssues(request.issues(), history);

        entityManager.flush();
        return new CourseImportResponse(
                request.importId(),
                StorageStatus.STORED,
                true,
                lectures.size(),
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

    private List<LectureRequest> deduplicateLectures(TimetableParseResultRequest request) {
        LinkedHashMap<String, LectureRequest> uniqueLectures = new LinkedHashMap<>();
        request.lectures().forEach(lecture -> uniqueLectures.putIfAbsent(
                courseKey(request, lecture),
                lecture
        ));
        return List.copyOf(uniqueLectures.values());
    }

    private Map<String, AcademicUnit> resolveAcademicUnits(
            TimetableParseResultRequest request,
            List<LectureRequest> lectures
    ) {
        LinkedHashMap<String, AcademicUnitRequest> requested = new LinkedHashMap<>();
        request.academicUnits().forEach(unit -> requested.put(academicUnitKey(unit), unit));
        lectures.stream()
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

    private List<ResolvedCourse> resolveCourses(
            TimetableParseResultRequest request,
            List<LectureRequest> lectures,
            Map<String, AcademicUnit> academicUnits
    ) {
        Set<String> masterKeys = lectures.stream()
                .map(lecture -> courseKey(request, lecture))
                .collect(Collectors.toSet());
        Map<String, Course> byMasterKey = masterKeys.isEmpty()
                ? Map.of()
                : courseRepository.findAllByMasterKeyIn(masterKeys)
                        .stream()
                        .collect(Collectors.toMap(
                                Course::getMasterKey,
                                Function.identity()
                        ));

        List<ResolvedCourse> result = new ArrayList<>(lectures.size());
        for (LectureRequest lecture : lectures) {
            String masterKey = courseKey(request, lecture);
            String courseCode = normalizeCourseCode(lecture.courseCode());
            Course course = byMasterKey.get(masterKey);

            AcademicUnit academicUnit = lecture.academicUnit() == null
                    ? null
                    : academicUnits.get(academicUnitKey(lecture.academicUnit()));
            if (course == null) {
                course = Course.createWithDetails(
                        masterKey,
                        courseCode,
                        lecture.courseName(),
                        academicUnit,
                        request.curriculumType(),
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
                entityManager.persist(course);
            } else {
                course.replaceDetails(
                        courseCode,
                        lecture.courseName(),
                        academicUnit,
                        request.curriculumType(),
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
            }
            result.add(new ResolvedCourse(lecture, course));
        }
        return result;
    }

    private Map<String, Classroom> resolveClassrooms(List<LectureRequest> lectures) {
        LinkedHashMap<String, ClassroomRequest> requested = new LinkedHashMap<>();
        lectures.stream()
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

    private void replaceScopeTaxonomy(Semester semester, CurriculumType curriculumType) {
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

    private List<ResolvedOffering> syncOfferings(
            Semester semester,
            TimetableParseResultRequest request,
            CourseImportHistory history,
            List<ResolvedCourse> courses
    ) {
        List<CourseOffering> existingOfferings = courseOfferingRepository
                .findAllBySemesterId(semester.getId());
        Map<UUID, CourseOffering> existingByCourse = existingOfferings.stream()
                .collect(Collectors.toMap(
                        offering -> offering.getCourse().getId(),
                        Function.identity()
                ));
        Set<UUID> incomingCourseIds = courses.stream()
                .map(resolved -> resolved.course().getId())
                .collect(Collectors.toSet());

        List<ResolvedOffering> result = new ArrayList<>(courses.size());
        for (ResolvedCourse resolved : courses) {
            LectureRequest lecture = resolved.lecture();
            CourseOffering offering = existingByCourse.get(resolved.course().getId());
            if (offering == null) {
                offering = CourseOffering.link(
                        semester,
                        resolved.course(),
                        history,
                        request.curriculumType(),
                        lecture.sourceSheet(),
                        lecture.sourceRow()
                );
                entityManager.persist(offering);
            } else {
                offering.refreshImportSource(
                        history,
                        request.curriculumType(),
                        lecture.sourceSheet(),
                        lecture.sourceRow()
                );
            }
            result.add(new ResolvedOffering(resolved, offering));
        }

        existingOfferings.stream()
                .filter(offering -> offering.getScopeCurriculumType()
                        == request.curriculumType())
                .filter(offering -> !incomingCourseIds.contains(offering.getCourse().getId()))
                .forEach(CourseOffering::deactivate);
        return result;
    }

    private void replaceCourseDetails(
            List<ResolvedCourse> courses,
            Map<String, Classroom> classrooms
    ) {
        List<UUID> courseIds = courses.stream()
                .map(resolved -> resolved.course().getId())
                .distinct()
                .toList();
        if (!courseIds.isEmpty()) {
            offeringGeneralEducationRepository.deleteByCourseIds(courseIds);
            offeringAllowedGradeRepository.deleteByCourseIds(courseIds);
            offeringEligibleDepartmentRepository.deleteByCourseIds(courseIds);
            courseScheduleRepository.deleteByCourseIds(courseIds);
            entityManager.flush();
        }

        for (ResolvedCourse resolved : courses) {
            LectureRequest lecture = resolved.lecture();
            Course course = resolved.course();

            if (lecture.generalEducation() != null) {
                persistGeneralEducation(course, lecture.generalEducation());
            }
            lecture.allowedGrades().stream()
                    .distinct()
                    .map(grade -> OfferingAllowedGrade.create(course, grade))
                    .forEach(entityManager::persist);
            lecture.eligibleDepartmentNames().stream()
                    .distinct()
                    .map(name -> OfferingEligibleDepartment.create(course, name))
                    .forEach(entityManager::persist);
            for (int scheduleIndex = 0; scheduleIndex < lecture.schedules().size(); scheduleIndex++) {
                var schedule = lecture.schedules().get(scheduleIndex);
                Classroom classroom = schedule.classroom() == null
                        ? null
                        : classrooms.get(classroomKey(schedule.classroom()));
                entityManager.persist(CourseSchedule.create(
                        course, scheduleIndex, schedule.dayOfWeek(), schedule.periods(), classroom
                ));
            }
        }
    }

    private void replaceSourceCells(List<ResolvedOffering> offerings) {
        List<UUID> offeringIds = offerings.stream()
                .map(resolved -> resolved.offering().getId())
                .toList();
        if (!offeringIds.isEmpty()) {
            courseSourceCellRepository.deleteByOfferingIds(offeringIds);
            entityManager.flush();
        }
        for (ResolvedOffering resolved : offerings) {
            for (SourceCellRequest cell : resolved.course().lecture().sourceCells()) {
                entityManager.persist(CourseSourceCell.create(
                        resolved.offering(),
                        cell.columnIndex(),
                        cell.headerName(),
                        cell.canonicalField(),
                        cell.value()
                ));
            }
        }
    }

    private void persistGeneralEducation(
            Course course,
            GeneralEducationContextRequest context
    ) {
        entityManager.persist(OfferingGeneralEducation.create(
                course,
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

    private String courseKey(
            TimetableParseResultRequest request,
            LectureRequest lecture
    ) {
        String normalizedCourseCode = normalizeCourseCode(lecture.courseCode());
        if (normalizedCourseCode != null) {
            return sha256("YEAR|%d|SEMESTER|%d|CURRICULUM|%s|CODE|%s|SECTION|%s".formatted(
                    request.academicYear(),
                    request.semester(),
                    request.curriculumType(),
                    normalizedCourseCode,
                    normalizeSectionNo(lecture.sectionNo())
            ));
        }
        return sha256("YEAR|%d|SEMESTER|%d|CURRICULUM|%s|SHEET|%s|ROW|%d".formatted(
                request.academicYear(),
                request.semester(),
                request.curriculumType(),
                lecture.sourceSheet(),
                lecture.sourceRow()
        ));
    }

    private String normalizeCourseCode(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) {
            return null;
        }
        return courseCode.strip().toUpperCase(Locale.ROOT);
    }

    private String normalizeSectionNo(String sectionNo) {
        if (sectionNo == null || sectionNo.isBlank()) {
            return "";
        }
        return sectionNo.strip().toUpperCase(Locale.ROOT);
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

    private record ResolvedCourse(
            LectureRequest lecture,
            Course course
    ) {
    }

    private record ResolvedOffering(
            ResolvedCourse course,
            CourseOffering offering
    ) {
    }
}
