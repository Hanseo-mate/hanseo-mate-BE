package hsu.hanseomate.domain.courseenrichment.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;

class ImportConcurrencyRetryTest {

    @Test
    void retriesSharedContentInsertRacesForBothImports() {
        for (String table : new String[]{"equivalent_course_contents", "cross_major_rule_contents"}) {
            AtomicInteger calls = new AtomicInteger();
            String result = ImportConcurrencyRetry.execute(table, "scope_constraint", () -> {
                if (calls.incrementAndGet() == 1) {
                    throw new DataIntegrityViolationException("insert failed",
                            new IllegalStateException("Duplicate entry for key '" + table + ".PRIMARY'"));
                }
                return "stored";
            });
            assertThat(result).isEqualTo("stored");
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    void unrelatedContentFailureIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        var failure = new DataIntegrityViolationException(
                "Null column in equivalent_course_contents");
        assertThatThrownBy(() -> ImportConcurrencyRetry.execute(
                "equivalent_course_contents", "scope_constraint", () -> {
                    calls.incrementAndGet();
                    throw failure;
                })).isSameAs(failure);
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesAreBoundedForBothUniqueAndLockFailures() {
        for (RuntimeException failure : new RuntimeException[]{
                new DataIntegrityViolationException("Duplicate entry uk_equivalent_active_scope"),
                new PessimisticLockingFailureException("deadlock")}) {
            AtomicInteger calls = new AtomicInteger();
            assertThatThrownBy(() -> ImportConcurrencyRetry.execute(
                    "equivalent_course_contents", "uk_equivalent_active_scope", () -> {
                        calls.incrementAndGet();
                        throw failure;
                    })).isSameAs(failure);
            assertThat(calls).hasValue(3);
        }
    }
}
