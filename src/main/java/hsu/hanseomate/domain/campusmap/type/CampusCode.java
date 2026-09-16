package hsu.hanseomate.domain.campusmap.type;

import hsu.hanseomate.domain.campusmap.support.CampusLocationNormalizer;
import java.util.Optional;
import java.util.regex.Pattern;

public enum CampusCode {
    SEOSAN,
    TAEAN;

    private static final Pattern SEOSAN_BUILDING_CODE = Pattern.compile(
            "H(?:0[1-9]|1[0-7])"
    );

    public static Optional<CampusCode> from(String value) {
        String normalized = CampusLocationNormalizer.normalize(value);
        if (SEOSAN_BUILDING_CODE.matcher(normalized).matches()) {
            return Optional.of(SEOSAN);
        }
        return switch (normalized) {
            case "SEOSAN", "서산", "서산캠", "서산캠퍼스" -> Optional.of(SEOSAN);
            case "TAEAN", "태안", "태안캠", "태안캠퍼스" -> Optional.of(TAEAN);
            default -> Optional.empty();
        };
    }
}
