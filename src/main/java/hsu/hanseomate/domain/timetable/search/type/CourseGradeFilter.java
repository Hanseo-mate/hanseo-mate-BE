package hsu.hanseomate.domain.timetable.search.type;

public enum CourseGradeFilter {
    GRADE_1("1학년", 1),
    GRADE_2("2학년", 2),
    GRADE_3("3학년", 3),
    GRADE_4("4학년", 4),
    OTHER("기타", null);

    private final String label;
    private final Integer grade;

    CourseGradeFilter(String label, Integer grade) {
        this.label = label;
        this.grade = grade;
    }

    public String getLabel() {
        return label;
    }

    public Integer getGrade() {
        return grade;
    }
}
