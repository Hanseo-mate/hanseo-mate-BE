package hsu.hanseomate.domain.campusmap.service;

import hsu.hanseomate.domain.campusmap.entity.CampusLectureBuildingDetail;
import hsu.hanseomate.domain.campusmap.repository.CampusLectureBuildingDetailRepository;
import hsu.hanseomate.domain.campusmap.support.CampusBuildingCatalog;
import hsu.hanseomate.domain.campusmap.support.CampusBuildingCatalog.CampusBuildingLocation;
import hsu.hanseomate.domain.campusmap.support.CampusBuildingCatalog.CampusBuildingQuery;
import hsu.hanseomate.domain.campusmap.support.CampusLocationNormalizer;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.domain.campusmap.type.CampusPlaceCategory;
import hsu.hanseomate.domain.courseimport.dto.ClassroomRequest;
import hsu.hanseomate.domain.courseimport.dto.LectureRequest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CampusBuildingDepartmentSnapshotService {

    private static final int MINIMUM_LECTURE_COUNT = 3;

    private final CampusBuildingCatalog buildingCatalog;
    private final CampusLectureBuildingDetailRepository detailRepository;

    /**
     * Replaces the current building department snapshot from one successful
     * major timetable import. Each deduplicated lecture counts at most once
     * for a building even when it has multiple schedules in that building.
     */
    public void replaceWith(List<LectureRequest> lectures) {
        List<CampusLectureBuildingDetail> details = detailRepository.findAll();
        Set<CampusBuildingQuery> queries = collectQueries(lectures, details);
        Map<CampusBuildingQuery, CampusBuildingLocation> resolved =
                buildingCatalog.findAll(queries);

        Map<CampusLectureBuildingDetail, BuildingKey> buildingByDetail =
                resolveDetails(details, resolved);
        Map<BuildingKey, Map<String, Integer>> lectureCounts =
                countLecturesByDepartmentAndBuilding(lectures, resolved);

        details.forEach(detail -> detail.replaceDepartments(
                qualifiedDepartments(lectureCounts.get(buildingByDetail.get(detail)))
        ));
    }

    private Set<CampusBuildingQuery> collectQueries(
            List<LectureRequest> lectures,
            List<CampusLectureBuildingDetail> details
    ) {
        Set<CampusBuildingQuery> queries = new LinkedHashSet<>();
        lectures.stream()
                .flatMap(lecture -> lecture.schedules().stream())
                .map(schedule -> schedule.classroom())
                .filter(this::hasBuildingName)
                .map(this::toQuery)
                .forEach(queries::add);
        details.stream()
                .filter(detail -> detail.getPlace().getCategory()
                        == CampusPlaceCategory.LECTURE_BUILDING)
                .map(detail -> new CampusBuildingQuery(
                        detail.getPlace().getCampusCode().name(),
                        detail.getPlace().getPlaceName()
                ))
                .forEach(queries::add);
        return queries;
    }

    private Map<CampusLectureBuildingDetail, BuildingKey> resolveDetails(
            List<CampusLectureBuildingDetail> details,
            Map<CampusBuildingQuery, CampusBuildingLocation> resolved
    ) {
        Map<CampusLectureBuildingDetail, BuildingKey> result =
                new LinkedHashMap<>();
        for (CampusLectureBuildingDetail detail : details) {
            if (detail.getPlace().getCategory()
                    != CampusPlaceCategory.LECTURE_BUILDING) {
                continue;
            }
            CampusBuildingQuery query = new CampusBuildingQuery(
                    detail.getPlace().getCampusCode().name(),
                    detail.getPlace().getPlaceName()
            );
            CampusBuildingLocation location = resolved.get(query);
            if (location != null) {
                result.put(detail, BuildingKey.from(location));
            }
        }
        return result;
    }

    private Map<BuildingKey, Map<String, Integer>>
            countLecturesByDepartmentAndBuilding(
                    List<LectureRequest> lectures,
                    Map<CampusBuildingQuery, CampusBuildingLocation> resolved
            ) {
        Map<BuildingKey, Map<String, Integer>> counts = new LinkedHashMap<>();
        for (LectureRequest lecture : lectures) {
            if (lecture.academicUnit() == null
                    || lecture.academicUnit().departmentName() == null
                    || lecture.academicUnit().departmentName().isBlank()) {
                continue;
            }
            String departmentName = lecture.academicUnit()
                    .departmentName()
                    .trim();
            Set<BuildingKey> lectureBuildings = new LinkedHashSet<>();
            lecture.schedules().stream()
                    .map(schedule -> schedule.classroom())
                    .filter(this::hasBuildingName)
                    .map(this::toQuery)
                    .map(resolved::get)
                    .filter(location -> location != null)
                    .map(BuildingKey::from)
                    .forEach(lectureBuildings::add);
            lectureBuildings.forEach(building -> counts
                    .computeIfAbsent(building, ignored -> new LinkedHashMap<>())
                    .merge(departmentName, 1, Integer::sum));
        }
        return counts;
    }

    private List<String> qualifiedDepartments(Map<String, Integer> counts) {
        if (counts == null) {
            return List.of();
        }
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= MINIMUM_LECTURE_COUNT)
                .map(Map.Entry::getKey)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private boolean hasBuildingName(ClassroomRequest classroom) {
        return classroom != null
                && classroom.buildingName() != null
                && !classroom.buildingName().isBlank();
    }

    private CampusBuildingQuery toQuery(ClassroomRequest classroom) {
        return new CampusBuildingQuery(
                classroom.campusCode(),
                classroom.buildingName()
        );
    }

    private record BuildingKey(
            CampusCode campusCode,
            String canonicalNameKey
    ) {
        private static BuildingKey from(CampusBuildingLocation location) {
            return new BuildingKey(
                    location.campusCode(),
                    CampusLocationNormalizer.normalize(
                            location.canonicalBuildingName()
                    )
            );
        }
    }
}
