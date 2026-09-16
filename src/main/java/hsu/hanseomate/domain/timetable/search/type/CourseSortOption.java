package hsu.hanseomate.domain.timetable.search.type;

public enum CourseSortOption {
    DEFAULT("기본"),
    COURSE_CODE("과목코드"),
    COURSE_NAME("과목명");

    private final String label;

    CourseSortOption(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
