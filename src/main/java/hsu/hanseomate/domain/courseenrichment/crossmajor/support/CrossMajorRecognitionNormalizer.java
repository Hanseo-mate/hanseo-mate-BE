package hsu.hanseomate.domain.courseenrichment.crossmajor.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CrossMajorRecognitionNormalizer {

    private static final Pattern CONTROL_WHITESPACE = Pattern.compile("[\\t\\r\\n]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIGITS = Pattern.compile("\\d{1,7}");

    private CrossMajorRecognitionNormalizer() {
    }

    public static String text(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00a0', ' ')
                .replace('\u3000', ' ');
        normalized = CONTROL_WHITESPACE.matcher(normalized).replaceAll(" ");
        return WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    }

    public static String key(String value) {
        return text(value).replace(" ", "").toLowerCase(Locale.ROOT);
    }

    public static String courseCode(String value) {
        String normalized = text(value);
        if (!DIGITS.matcher(normalized).matches()) {
            return null;
        }
        return "0".repeat(7 - normalized.length()) + normalized;
    }
}
