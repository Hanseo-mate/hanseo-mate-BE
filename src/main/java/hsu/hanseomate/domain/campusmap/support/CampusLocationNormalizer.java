package hsu.hanseomate.domain.campusmap.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CampusLocationNormalizer {

    private static final Pattern NAME_SEPARATORS = Pattern.compile("[\\s_]+");

    private CampusLocationNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toUpperCase(Locale.ROOT);
        return NAME_SEPARATORS.matcher(normalized).replaceAll("");
    }
}
