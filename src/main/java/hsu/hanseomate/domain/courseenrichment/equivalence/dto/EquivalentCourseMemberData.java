package hsu.hanseomate.domain.courseenrichment.equivalence.dto;

public record EquivalentCourseMemberData(
        String courseCode,
        String courseName,
        String sourceSheet,
        int sourceRow,
        int memberOrder
) {
}
