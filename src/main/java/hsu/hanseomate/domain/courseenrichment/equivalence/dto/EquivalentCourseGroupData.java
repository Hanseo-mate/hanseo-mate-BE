package hsu.hanseomate.domain.courseenrichment.equivalence.dto;

import java.util.List;

public record EquivalentCourseGroupData(
        int sourceSerial,
        int groupOrder,
        String sourceSheet,
        int sourceStartRow,
        int sourceEndRow,
        List<EquivalentCourseMemberData> members
) {
    public EquivalentCourseGroupData {
        members = List.copyOf(members);
    }
}
