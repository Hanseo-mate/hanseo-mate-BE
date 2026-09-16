package hsu.hanseomate.domain.course.repository;

import java.util.UUID;

public interface OfferingEligibleDepartmentNameProjection {

    UUID getCourseId();

    String getDepartmentName();
}
