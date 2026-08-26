package hsu.hanseomate.domain.campusmap.service;

import hsu.hanseomate.domain.campusmap.dto.CampusMapCourseLocationResponse;
import hsu.hanseomate.domain.campusmap.dto.CampusMapLocationStatus;
import hsu.hanseomate.domain.campusmap.dto.CampusMapTodayResponse;
import hsu.hanseomate.domain.campusmap.support.CampusBuildingCatalog;
import hsu.hanseomate.domain.campusmap.support.CampusBuildingCatalog.CampusBuildingLocation;
import hsu.hanseomate.domain.campusmap.support.CampusBuildingCatalog.CampusBuildingQuery;
import hsu.hanseomate.domain.course.entity.Classroom;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import hsu.hanseomate.domain.timetable.composition.currentuser.CurrentUserIdProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampusMapService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final CourseScheduleRepository courseScheduleRepository;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final CampusBuildingCatalog campusBuildingCatalog;
    private final Clock clock;

    public CampusMapTodayResponse getTodayLocations() {
        Long ownerId = currentUserIdProvider.currentUserId();
        LocalDate today = LocalDate.now(clock.withZone(KOREA_ZONE));
        int semester = today.getMonthValue() <= 6 ? 1 : 2;
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(today.getDayOfWeek().name());

        List<CourseSchedule> schedules = courseScheduleRepository
                .findTimetableSchedules(
                        ownerId,
                        today.getYear(),
                        semester,
                        dayOfWeek
                ).stream()
                .sorted(Comparator
                        .comparingInt((CourseSchedule schedule) ->
                                firstPeriod(schedule.getPeriods()))
                        .thenComparing(
                                schedule -> schedule.getCourse().getCourseName(),
                                Comparator.nullsLast(String::compareTo)
                        )
                        .thenComparingInt(CourseSchedule::getScheduleOrder)
                        .thenComparing(CourseSchedule::getId))
                .toList();
        Map<CampusBuildingQuery, CampusBuildingLocation> buildingLocations =
                campusBuildingCatalog.findAll(schedules.stream()
                        .map(CourseSchedule::getClassroom)
                        .filter(classroom -> classroom != null)
                        .map(this::buildingQuery)
                        .toList());
        List<CampusMapCourseLocationResponse> locations = schedules.stream()
                .map(schedule -> toResponse(schedule, buildingLocations))
                .toList();

        return new CampusMapTodayResponse(
                today,
                dayOfWeek,
                today.getYear(),
                semester,
                locations
        );
    }

    private CampusMapCourseLocationResponse toResponse(
            CourseSchedule schedule,
            Map<CampusBuildingQuery, CampusBuildingLocation> buildingLocations
    ) {
        List<Integer> periods = schedule.getPeriods().stream()
                .distinct()
                .sorted()
                .toList();
        Classroom classroom = schedule.getClassroom();
        if (classroom == null) {
            return new CampusMapCourseLocationResponse(
                    schedule.getId(),
                    schedule.getCourse().getCourseName(),
                    periods,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    CampusMapLocationStatus.NO_CLASSROOM
            );
        }

        CampusBuildingLocation mapped = buildingLocations.get(
                buildingQuery(classroom)
        );
        if (mapped == null) {
            return new CampusMapCourseLocationResponse(
                    schedule.getId(),
                    schedule.getCourse().getCourseName(),
                    periods,
                    classroom.getCampusCode(),
                    classroom.getBuildingName(),
                    classroom.getRoomNumber(),
                    null,
                    null,
                    null,
                    CampusMapLocationStatus.UNMAPPED
            );
        }

        return new CampusMapCourseLocationResponse(
                schedule.getId(),
                schedule.getCourse().getCourseName(),
                periods,
                mapped.campusCode(),
                classroom.getBuildingName(),
                classroom.getRoomNumber(),
                mapped.canonicalBuildingName(),
                mapped.latitude(),
                mapped.longitude(),
                CampusMapLocationStatus.MAPPED
        );
    }

    private CampusBuildingQuery buildingQuery(Classroom classroom) {
        return new CampusBuildingQuery(
                classroom.getCampusCode(),
                classroom.getBuildingName()
        );
    }

    private int firstPeriod(List<Integer> periods) {
        return periods.stream().min(Integer::compareTo).orElse(Integer.MAX_VALUE);
    }
}
