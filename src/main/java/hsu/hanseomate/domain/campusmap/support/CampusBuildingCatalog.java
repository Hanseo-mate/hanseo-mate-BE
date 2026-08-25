package hsu.hanseomate.domain.campusmap.support;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Campus building marker coordinates.
 *
 * <p>Building names are based on the Hanseo University campus guide. Coordinates
 * are marker positions checked against public maps on 2026-08-25. Unknown names
 * must remain unmapped instead of being matched by prefix or substring.</p>
 */
@Component
public class CampusBuildingCatalog {

    private static final String SEOSAN = "SEOSAN";
    private static final String TAEAN = "TAEAN";
    private static final Pattern NAME_SEPARATORS = Pattern.compile("[\\s_]+");

    private static final List<BuildingDefinition> BUILDINGS = List.of(
            building(SEOSAN, "공학관", 36.6909679, 126.5858094,
                    "공학관"),
            building(SEOSAN, "인문사회관", 36.6900568, 126.5858982,
                    "인문사회관", "인문관"),
            building(SEOSAN, "자악관", 36.6914647, 126.5889642,
                    "자악관", "본관", "서산 본관", "서산본관"),
            building(SEOSAN, "보건의료학관", 36.6902179, 126.5818931,
                    "보건의료학관", "보건관"),
            building(SEOSAN, "건축토목공학관", 36.6913449, 126.5835591,
                    "건축토목공학관", "건축관"),
            building(SEOSAN, "인곡관", 36.6917739, 126.5847621,
                    "인곡관"),
            building(SEOSAN, "예술관", 36.6893088, 126.5879975,
                    "예술관"),
            building(SEOSAN, "이학관", 36.690682459, 126.58171696,
                    "이학관"),
            building(SEOSAN, "영암관", 36.6912872, 126.5824797,
                    "영암관"),
            building(SEOSAN, "심운관", 36.69116, 126.58648,
                    "심운관"),
            building(SEOSAN, "영암체육관", 36.6915408, 126.5882049,
                    "영암체육관", "영암체육관(서산)", "서산 영암체육관"),
            building(TAEAN, "태안 강의동(본관)", 36.5944988, 126.294045,
                    "본관", "태안 강의동(본관)", "태안 강의동 본관",
                    "태안강의동 본관", "비행교육원"),
            building(TAEAN, "태안 실습2동", 36.5934316, 126.2948762,
                    "실습2동", "태안 실습2동", "태안실습2동"),
            building(TAEAN, "항공기술교육센터(메디치)",
                    36.5965443, 126.2924351,
                    "항공기술교육센터(메디치)",
                    "태안 항공기술교육센터(메디치)",
                    "태안 항공기술센터(메디치)")
    );

    private static final Map<String, List<CampusBuildingLocation>> BY_ALIAS =
            indexByAlias(BUILDINGS);

    public Optional<CampusBuildingLocation> find(
            String campusCode,
            String buildingName
    ) {
        String alias = normalize(buildingName);
        if (alias.isEmpty()) {
            return Optional.empty();
        }

        List<CampusBuildingLocation> candidates = BY_ALIAS.getOrDefault(
                alias,
                List.of()
        );
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        String rawNormalizedCampus = normalize(campusCode);
        if (!rawNormalizedCampus.isEmpty()) {
            Optional<String> normalizedCampus = normalizeKnownCampus(
                    rawNormalizedCampus
            );
            if (normalizedCampus.isEmpty()) {
                return Optional.empty();
            }
            return candidates.stream()
                    .filter(location -> normalizedCampus.orElseThrow()
                            .equals(location.campusCode()))
                    .findFirst();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }
        return Optional.empty();
    }

    private static BuildingDefinition building(
            String campusCode,
            String canonicalBuildingName,
            double latitude,
            double longitude,
            String... aliases
    ) {
        CampusBuildingLocation location = new CampusBuildingLocation(
                campusCode,
                canonicalBuildingName,
                latitude,
                longitude
        );
        return new BuildingDefinition(location, List.of(aliases));
    }

    private static Map<String, List<CampusBuildingLocation>> indexByAlias(
            List<BuildingDefinition> definitions
    ) {
        Map<String, List<CampusBuildingLocation>> mutableIndex = new HashMap<>();
        for (BuildingDefinition definition : definitions) {
            Set<String> aliases = new LinkedHashSet<>(definition.aliases());
            aliases.add(definition.location().canonicalBuildingName());
            Set<String> normalizedAliases = new LinkedHashSet<>();
            aliases.stream().map(CampusBuildingCatalog::normalize)
                    .forEach(normalizedAliases::add);
            for (String alias : normalizedAliases) {
                mutableIndex.computeIfAbsent(alias, ignored -> new ArrayList<>())
                        .add(definition.location());
            }
        }

        Map<String, List<CampusBuildingLocation>> immutableIndex = new HashMap<>();
        mutableIndex.forEach((alias, locations) ->
                immutableIndex.put(alias, List.copyOf(locations)));
        return Map.copyOf(immutableIndex);
    }

    private static Optional<String> normalizeKnownCampus(String campusCode) {
        String normalized = normalize(campusCode);
        return switch (normalized) {
            case "SEOSAN", "서산", "서산캠퍼스" -> Optional.of(SEOSAN);
            case "TAEAN", "태안", "태안캠퍼스" -> Optional.of(TAEAN);
            default -> Optional.empty();
        };
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toUpperCase(Locale.ROOT);
        return NAME_SEPARATORS.matcher(normalized).replaceAll("");
    }

    private record BuildingDefinition(
            CampusBuildingLocation location,
            List<String> aliases
    ) {
    }

    public record CampusBuildingLocation(
            String campusCode,
            String canonicalBuildingName,
            double latitude,
            double longitude
    ) {
    }
}
