package hsu.hanseomate.domain.campusmap.type;

import hsu.hanseomate.domain.campusmap.support.CampusLocationNormalizer;
import java.util.Optional;

public enum CampusCode {
    SEOSAN,
    TAEAN;

    public static Optional<CampusCode> from(String value) {
        return switch (CampusLocationNormalizer.normalize(value)) {
            case "SEOSAN", "서산", "서산캠", "서산캠퍼스" -> Optional.of(SEOSAN);
            case "TAEAN", "태안", "태안캠", "태안캠퍼스" -> Optional.of(TAEAN);
            default -> Optional.empty();
        };
    }
}
