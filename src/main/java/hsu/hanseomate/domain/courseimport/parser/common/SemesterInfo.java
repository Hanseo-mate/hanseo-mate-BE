package hsu.hanseomate.domain.courseimport.parser.common;

public record SemesterInfo(int academicYear, int semester) {

    public String displayName() {
        return academicYear + "학년도 " + semester + "학기";
    }
}
