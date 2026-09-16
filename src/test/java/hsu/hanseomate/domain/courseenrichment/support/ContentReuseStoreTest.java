package hsu.hanseomate.domain.courseenrichment.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ContentReuseStoreTest {

    @Test
    void batchesLookupsAndPersistsOnlyMissingUniqueContent() {
        EntityManager entityManager = mock(EntityManager.class);
        ContentReuseStore store = new ContentReuseStore(entityManager);
        List<Content> existing = IntStream.range(0, 500)
                .mapToObj(index -> new Content("%04d".formatted(index))).toList();
        Content added = new Content("0500");
        List<Content> incoming = new ArrayList<>(existing);
        incoming.add(added);
        incoming.add(new Content("0000"));
        List<Integer> lookupSizes = new ArrayList<>();

        Map<String, Content> resolved = store.resolve(incoming, Content::key, keys -> {
            lookupSizes.add(keys.size());
            return existing.stream().filter(content -> keys.contains(content.key())).toList();
        });

        assertThat(lookupSizes).containsExactly(500, 1);
        assertThat(resolved).hasSize(501);
        assertThat(resolved.get("0000")).isSameAs(existing.get(0));
        assertThat(resolved.get("0500")).isSameAs(added);
        verify(entityManager, times(1)).persist(added);
        verifyNoMoreInteractions(entityManager);
    }

    @Test
    void emptySnapshotDoesNotQueryOrInsert() {
        EntityManager entityManager = mock(EntityManager.class);
        var resolved = new ContentReuseStore(entityManager).resolve(
                List.<Content>of(), Content::key, keys -> {
                    throw new AssertionError("Empty content must not query");
                });
        assertThat(resolved).isEmpty();
        verifyNoMoreInteractions(entityManager);
    }

    private record Content(String key) {
    }
}
