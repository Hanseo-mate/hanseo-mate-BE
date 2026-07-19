package hsu.hanseomate.domain.course.service;

import tools.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.course.dto.CourseOfferingResponse;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseQueryService {

    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final ObjectMapper objectMapper;

    public List<CourseOfferingResponse> search(
            Integer academicYear,
            Integer semester,
            CurriculumType curriculumType,
            String academicUnit,
            GeneralClassification classification,
            GeneralArea area,
            DeliveryProvider deliveryProvider,
            String courseName,
            String instructorName
    ) {
        List<CourseOffering> offerings = courseOfferingRepository.search(
                academicYear,
                semester,
                curriculumType,
                normalize(academicUnit),
                classification,
                area,
                deliveryProvider,
                normalize(courseName),
                normalize(instructorName)
        );
        if (offerings.isEmpty()) {
            return List.of();
        }

        List<UUID> offeringIds = offerings.stream().map(CourseOffering::getId).toList();
        Map<UUID, List<CourseSchedule>> schedulesByOffering = courseScheduleRepository
                .findAllForOfferings(offeringIds)
                .stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getOffering().getId()));

        return offerings.stream()
                .map(offering -> CourseOfferingResponse.from(
                        offering,
                        schedulesByOffering.getOrDefault(offering.getId(), List.of()),
                        objectMapper
                ))
                .toList();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
