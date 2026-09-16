package hsu.hanseomate.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class WholeNumberValidator implements ConstraintValidator<WholeNumber, BigDecimal> {

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        return value == null || value.scale() <= 0;
    }
}
