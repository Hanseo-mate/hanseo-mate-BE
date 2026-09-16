package hsu.hanseomate.domain.courseenrichment.crossmajor.dto;

public record CrossMajorRecognitionRuleData(
        String ruleKey,
        String studentCollegeName,
        String studentDepartmentName,
        String studentMajorName,
        String offeringCollegeName,
        String offeringDepartmentName,
        String offeringMajorName,
        String offeringDepartmentKey,
        String offeringMajorKey,
        String courseCode,
        String courseName,
        String courseNameKey,
        int effectiveYear,
        int effectiveSemester,
        String sourceSheet,
        int sourceRow
) {
    public String canonicalLine() {
        return String.join("\u001f",
                studentCollegeName,
                studentDepartmentName,
                studentMajorName,
                offeringCollegeName,
                offeringDepartmentName,
                offeringMajorName,
                courseCode,
                courseName,
                Integer.toString(effectiveYear),
                Integer.toString(effectiveSemester)
        );
    }
}
