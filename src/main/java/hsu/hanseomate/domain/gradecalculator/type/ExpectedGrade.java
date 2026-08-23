package hsu.hanseomate.domain.gradecalculator.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigDecimal;
import java.util.Arrays;

public enum ExpectedGrade {

    A_PLUS("A+", "4.5", true, true),
    A("A", "4.0", true, true),
    B_PLUS("B+", "3.5", true, true),
    B("B", "3.0", true, true),
    C_PLUS("C+", "2.5", true, true),
    C("C", "2.0", true, true),
    D_PLUS("D+", "1.5", true, true),
    D("D", "1.0", true, true),
    P("P", null, false, true),
    F("F", "0.0", true, false);

    private final String code;
    private final BigDecimal gradePoint;
    private final boolean includedInGpa;
    private final boolean creditEarned;

    ExpectedGrade(
            String code,
            String gradePoint,
            boolean includedInGpa,
            boolean creditEarned
    ) {
        this.code = code;
        this.gradePoint = gradePoint == null ? null : new BigDecimal(gradePoint);
        this.includedInGpa = includedInGpa;
        this.creditEarned = creditEarned;
    }

    @JsonCreator
    public static ExpectedGrade fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        return Arrays.stream(values())
                .filter(grade -> grade.code.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 예상 성적입니다: " + code
                ));
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public BigDecimal getGradePoint() {
        return gradePoint;
    }

    public boolean isIncludedInGpa() {
        return includedInGpa;
    }

    public boolean isCreditEarned() {
        return creditEarned;
    }
}
