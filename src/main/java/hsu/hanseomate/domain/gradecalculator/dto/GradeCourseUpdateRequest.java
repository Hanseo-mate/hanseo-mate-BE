package hsu.hanseomate.domain.gradecalculator.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import hsu.hanseomate.domain.gradecalculator.type.ExpectedGrade;
import java.math.BigDecimal;

public final class GradeCourseUpdateRequest {

    private String courseName;
    private boolean courseNamePresent;
    private BigDecimal credit;
    private boolean creditPresent;
    private ExpectedGrade expectedGrade;
    private boolean expectedGradePresent;

    public GradeCourseUpdateRequest() {
    }

    @JsonSetter("courseName")
    public void setCourseName(String courseName) {
        this.courseName = courseName;
        this.courseNamePresent = true;
    }

    @JsonSetter("credit")
    public void setCredit(BigDecimal credit) {
        this.credit = credit;
        this.creditPresent = true;
    }

    @JsonSetter("expectedGrade")
    public void setExpectedGrade(ExpectedGrade expectedGrade) {
        this.expectedGrade = expectedGrade;
        this.expectedGradePresent = true;
    }

    public ExpectedGrade expectedGrade() {
        return expectedGrade;
    }

    public String courseName() {
        return courseName;
    }

    public boolean hasCourseName() {
        return courseNamePresent;
    }

    public BigDecimal credit() {
        return credit;
    }

    public boolean hasCredit() {
        return creditPresent;
    }

    public boolean hasExpectedGrade() {
        return expectedGradePresent;
    }

    public boolean hasAnyField() {
        return courseNamePresent || creditPresent || expectedGradePresent;
    }
}
