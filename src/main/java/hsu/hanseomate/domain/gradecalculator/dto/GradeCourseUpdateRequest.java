package hsu.hanseomate.domain.gradecalculator.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import hsu.hanseomate.domain.gradecalculator.type.ExpectedGrade;

public final class GradeCourseUpdateRequest {

    private ExpectedGrade expectedGrade;
    private boolean expectedGradePresent;

    public GradeCourseUpdateRequest() {
    }

    @JsonSetter("expectedGrade")
    public void setExpectedGrade(ExpectedGrade expectedGrade) {
        this.expectedGrade = expectedGrade;
        this.expectedGradePresent = true;
    }

    public ExpectedGrade expectedGrade() {
        return expectedGrade;
    }

    public boolean hasExpectedGrade() {
        return expectedGradePresent;
    }
}
