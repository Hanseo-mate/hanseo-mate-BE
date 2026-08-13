package hsu.hanseomate.domain.courseenrichment.crossmajor.dto;

public record CrossMajorRecognitionResponse(
        String studentCollegeName,
        String studentDepartmentName,
        String studentMajorName,
        int effectiveYear,
        int effectiveSemester
) implements Comparable<CrossMajorRecognitionResponse> {

    @Override
    public int compareTo(CrossMajorRecognitionResponse other) {
        int compared = studentCollegeName.compareTo(other.studentCollegeName);
        if (compared != 0) return compared;
        compared = studentDepartmentName.compareTo(other.studentDepartmentName);
        if (compared != 0) return compared;
        compared = studentMajorName.compareTo(other.studentMajorName);
        if (compared != 0) return compared;
        compared = Integer.compare(effectiveYear, other.effectiveYear);
        return compared != 0
                ? compared
                : Integer.compare(effectiveSemester, other.effectiveSemester);
    }
}
