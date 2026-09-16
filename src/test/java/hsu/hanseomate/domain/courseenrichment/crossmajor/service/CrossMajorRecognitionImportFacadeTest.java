package hsu.hanseomate.domain.courseenrichment.crossmajor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionImportResponse;
import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionParseResult;
import hsu.hanseomate.domain.courseenrichment.crossmajor.parser.CrossMajorRecognitionWorkbookParser;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

class CrossMajorRecognitionImportFacadeTest {

    private CrossMajorRecognitionWorkbookParser parser;
    private CrossMajorRecognitionImportService importService;
    private CrossMajorRecognitionImportFacade facade;
    private CrossMajorRecognitionParseResult parsed;
    private MockMultipartFile file;

    @BeforeEach
    void setUp() {
        parser = mock(CrossMajorRecognitionWorkbookParser.class);
        importService = mock(CrossMajorRecognitionImportService.class);
        facade = new CrossMajorRecognitionImportFacade(parser, importService, 10 * 1024 * 1024);
        parsed = new CrossMajorRecognitionParseResult(
                "2026학년도 1학기.xlsx",
                "a".repeat(64),
                "b".repeat(64),
                2026,
                1,
                "rules",
                0,
                List.of(),
                List.of()
        );
        file = new MockMultipartFile(
                "file",
                "2026학년도 1학기.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{'P', 'K'}
        );
        when(parser.parse(any(byte[].class), eq(file.getOriginalFilename())))
                .thenReturn(parsed);
    }

    @Test
    void retriesOnceAfterConcurrentFirstActiveScopeInsert() {
        CrossMajorRecognitionImportResponse stored = response();
        when(importService.importParsed(parsed))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for constraint uk_cross_major_active_scope"
                ))
                .thenReturn(stored);

        assertThat(facade.importWorkbook(file)).isSameAs(stored);
        verify(importService, times(2)).importParsed(parsed);
    }

    @Test
    void unrelatedIntegrityViolationIsNotRetried() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("unrelated foreign key failure");
        when(importService.importParsed(parsed)).thenThrow(failure);

        assertThatThrownBy(() -> facade.importWorkbook(file)).isSameAs(failure);
        verify(importService).importParsed(parsed);
    }

    private CrossMajorRecognitionImportResponse response() {
        return new CrossMajorRecognitionImportResponse(
                UUID.randomUUID(),
                StorageStatus.STORED,
                true,
                2026,
                1,
                0,
                "stored",
                List.of()
        );
    }
}
