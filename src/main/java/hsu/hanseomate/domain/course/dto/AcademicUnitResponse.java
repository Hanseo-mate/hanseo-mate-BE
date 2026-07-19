package hsu.hanseomate.domain.course.dto;

import hsu.hanseomate.domain.course.entity.AcademicUnit;

public record AcademicUnitResponse(
        String originalName,
        String departmentName,
        String majorName
) {
    public static AcademicUnitResponse from(AcademicUnit unit) {
        if (unit == null) {
            return null;
        }
        return new AcademicUnitResponse(
                unit.getOriginalName(), unit.getDepartmentName(), unit.getMajorName()
        );
    }
}
