package hsu.hanseomate.domain.timetable.search.type;

public enum CourseCreditFilter {
    CREDIT_1("1학점"),
    CREDIT_2("2학점"),
    CREDIT_3("3학점"),
    CREDIT_4_OR_MORE("4학점 이상");

    private final String label;

    CourseCreditFilter(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
