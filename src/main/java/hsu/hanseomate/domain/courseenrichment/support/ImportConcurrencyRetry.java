package hsu.hanseomate.domain.courseenrichment.support;

import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;

public final class ImportConcurrencyRetry {

    private static final int MAX_ATTEMPTS = 3;

    private ImportConcurrencyRetry() {
    }

    // Each invocation must enter a new transaction through the import service proxy.
    public static <T> T execute(String contentTable, String scopeConstraint, Supplier<T> importCall) {
        for (int attempt = 1; ; attempt++) {
            try {
                return importCall.get();
            } catch (DataIntegrityViolationException failure) {
                if (attempt == MAX_ATTEMPTS || !isUniqueRace(failure, contentTable, scopeConstraint)) {
                    throw failure;
                }
            } catch (PessimisticLockingFailureException failure) {
                if (attempt == MAX_ATTEMPTS) {
                    throw failure;
                }
            }
        }
    }

    private static boolean isUniqueRace(Throwable failure, String contentTable, String scopeConstraint) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            String normalized = message.toLowerCase(Locale.ROOT);
            if (normalized.contains(scopeConstraint)
                    || normalized.contains("active_scope_key")
                    || normalized.contains("activescopekey")) {
                return true;
            }
            if (normalized.contains(contentTable)
                    && (normalized.contains("duplicate") || normalized.contains("unique"))) {
                return true;
            }
        }
        return false;
    }
}
