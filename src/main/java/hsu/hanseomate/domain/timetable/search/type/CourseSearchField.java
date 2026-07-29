package hsu.hanseomate.domain.timetable.search.type;

public enum CourseSearchField {
    COURSE_NAME("과목명"),
    INSTRUCTOR_NAME("교수명"),
    COURSE_CODE("과목코드"),
    LOCATION("장소");

    private final String label;

    CourseSearchField(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
