package hsu.hanseomate.domain.courseenrichment.support;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentReuseStore {

    private static final int LOOKUP_BATCH_SIZE = 500;
    private final EntityManager entityManager;

    public void flush() {
        entityManager.flush();
    }

    // The caller owns the import transaction. Load in batches, then persist only missing
    // immutable content. Assigned hash IDs use persist, avoiding save/merge's per-row lookup.
    public <T> Map<String, T> resolve(
            Collection<T> candidates,
            Function<T, String> keyOf,
            Function<List<String>, List<T>> loadExisting
    ) {
        Map<String, T> unique = new TreeMap<>();
        candidates.forEach(candidate -> unique.putIfAbsent(keyOf.apply(candidate), candidate));
        List<String> keys = List.copyOf(unique.keySet());
        Map<String, T> resolved = new HashMap<>();
        for (int offset = 0; offset < keys.size(); offset += LOOKUP_BATCH_SIZE) {
            loadExisting.apply(keys.subList(offset, Math.min(offset + LOOKUP_BATCH_SIZE, keys.size())))
                    .forEach(existing -> resolved.put(keyOf.apply(existing), existing));
        }
        // Consistent key order reduces deadlocks when independent scopes share new content.
        unique.forEach((key, candidate) -> {
            if (!resolved.containsKey(key)) {
                entityManager.persist(candidate);
                resolved.put(key, candidate);
            }
        });
        return resolved;
    }
}
