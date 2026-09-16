package hsu.hanseomate.domain.courseenrichment.equivalence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseImportResponse;
import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseParseResult;
import hsu.hanseomate.domain.courseenrichment.equivalence.parser.EquivalentCourseWorkbookParser;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.mock.web.MockMultipartFile;

class EquivalentCourseImportFacadeTest {

    private EquivalentCourseWorkbookParser parser;
    private EquivalentCourseImportService importService;
    private EquivalentCourseImportFacade facade;
    private EquivalentCourseParseResult parsed;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        parser = mock(EquivalentCourseWorkbookParser.class);
        importService = mock(EquivalentCourseImportService.class);
        facade = new EquivalentCourseImportFacade(parser, importService, 10 * 1024 * 1024);
        parsed = new EquivalentCourseParseResult(
                "1.0",
                "test",
                "import-id",
                "2026-2 동일교과목현황.xlsx",
                "a".repeat(64),
                "b".repeat(64),
                2026,
                2,
                List.of(),
                List.of()
        );
        file = new MockMultipartFile(
                "file",
                parsed.fileName(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{'P', 'K'}
        );
        when(parser.parse(any(byte[].class), eq(file.getOriginalFilename())))
                .thenReturn(parsed);
    }

    @Test
    void retriesOnceAfterConcurrentFirstActiveScopeInsert() {
        EquivalentCourseImportResponse stored = response();
        when(importService.importSnapshot(parsed))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for constraint uk_equivalent_active_scope"
                ))
                .thenReturn(stored);

        assertThat(facade.importWorkbook(file)).isSameAs(stored);
        verify(importService, times(2)).importSnapshot(parsed);
    }

    @Test
    void retriesOnceAfterConcurrentScopeLockFailure() {
        EquivalentCourseImportResponse stored = response();
        when(importService.importSnapshot(parsed))
                .thenThrow(new PessimisticLockingFailureException("deadlock"))
                .thenReturn(stored);

        assertThat(facade.importWorkbook(file)).isSameAs(stored);
        verify(importService, times(2)).importSnapshot(parsed);
    }

    @Test
    void unrelatedIntegrityViolationIsNotRetried() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("unrelated foreign key failure");
        when(importService.importSnapshot(parsed)).thenThrow(failure);

        assertThatThrownBy(() -> facade.importWorkbook(file)).isSameAs(failure);
        verify(importService).importSnapshot(parsed);
    }

    private EquivalentCourseImportResponse response() {
        return new EquivalentCourseImportResponse(
                parsed.importId(),
                StorageStatus.STORED,
                true,
                0,
                0,
                "stored",
                List.of()
        );
    }
}
