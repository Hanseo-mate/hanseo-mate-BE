package hsu.hanseomate.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            boolean isHttp = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return isHttp && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
