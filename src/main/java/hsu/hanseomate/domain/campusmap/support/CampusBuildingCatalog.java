package hsu.hanseomate.domain.campusmap.support;

import hsu.hanseomate.domain.campusmap.entity.CampusBuilding;
import hsu.hanseomate.domain.campusmap.repository.CampusBuildingAliasRepository;
import hsu.hanseomate.domain.campusmap.repository.CampusBuildingRepository;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves imported classroom names against campus-specific building aliases.
 * Unknown or ambiguous aliases remain unmapped instead of using partial matches.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampusBuildingCatalog {

    private final CampusBuildingAliasRepository aliasRepository;
    private final CampusBuildingRepository buildingRepository;

    public Optional<CampusBuildingLocation> find(
            String campusCode,
            String buildingName
    ) {
        CampusBuildingQuery query = new CampusBuildingQuery(
                campusCode,
                buildingName
        );
        return Optional.ofNullable(findAll(List.of(query)).get(query));
    }

    public Map<CampusBuildingQuery, CampusBuildingLocation> findAll(
            Collection<CampusBuildingQuery> queries
    ) {
        Set<String> aliasKeys = queries.stream()
                .map(CampusBuildingQuery::buildingName)
                .map(CampusLocationNormalizer::normalize)
                .filter(aliasKey -> !aliasKey.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (aliasKeys.isEmpty()) {
            return Map.of();
        }

        Map<String, List<CampusBuildingLocation>> locationsByAliasKey =
                new LinkedHashMap<>();
        aliasRepository.findAllWithBuildingByAliasKeyIn(aliasKeys)
                .forEach(alias -> addLocation(
                        locationsByAliasKey,
                        alias.getAliasKey(),
                        toLocation(alias.getBuilding())
                ));
        buildingRepository.findAllByCanonicalNameKeyIn(aliasKeys)
                .forEach(building -> addLocation(
                        locationsByAliasKey,
                        building.getCanonicalNameKey(),
                        toLocation(building)
                ));

        Map<CampusBuildingQuery, CampusBuildingLocation> resolved =
                new LinkedHashMap<>();
        for (CampusBuildingQuery query : queries) {
            resolve(
                    query.campusCode(),
                    locationsByAliasKey.getOrDefault(
                            CampusLocationNormalizer.normalize(
                                    query.buildingName()
                            ),
                            List.of()
                    )
            ).ifPresent(location -> resolved.put(query, location));
        }
        return Map.copyOf(resolved);
    }

    private Optional<CampusBuildingLocation> resolve(
            String rawCampusCode,
            List<CampusBuildingLocation> candidates
    ) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        String normalizedCampus = CampusLocationNormalizer.normalize(
                rawCampusCode
        );
        if (!normalizedCampus.isEmpty()) {
            Optional<CampusCode> campusCode = CampusCode.from(rawCampusCode);
            if (campusCode.isEmpty()) {
                return Optional.empty();
            }
            return exactlyOne(candidates.stream()
                    .filter(location -> campusCode.orElseThrow().name()
                            .equals(location.campusCode()))
                    .toList());
        }
        return exactlyOne(candidates);
    }

    private Optional<CampusBuildingLocation> exactlyOne(
            List<CampusBuildingLocation> candidates
    ) {
        List<CampusBuildingLocation> distinct = candidates.stream()
                .distinct()
                .toList();
        if (distinct.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(distinct.get(0));
    }

    private static void addLocation(
            Map<String, List<CampusBuildingLocation>> locationsByAliasKey,
            String aliasKey,
            CampusBuildingLocation location
    ) {
        locationsByAliasKey.computeIfAbsent(
                aliasKey,
                ignored -> new ArrayList<>()
        ).add(location);
    }

    private static CampusBuildingLocation toLocation(CampusBuilding building) {
        return new CampusBuildingLocation(
                building.getCampusCode().name(),
                building.getCanonicalName(),
                building.getLatitude().doubleValue(),
                building.getLongitude().doubleValue()
        );
    }

    public record CampusBuildingQuery(
            String campusCode,
            String buildingName
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
