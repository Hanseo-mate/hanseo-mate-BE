package hsu.hanseomate.domain.courseimport.parser.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class SheetViewTest {

    @Test
    void resolvesMergedCellsToTheAnchorAndAccountsForTheWholeRange() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("merged");
            Cell anchor = sheet.createRow(1).createCell(1);
            anchor.setCellValue("병합 값");
            sheet.addMergedRegion(new CellRangeAddress(1, 3, 1, 3));

            SheetView view = new SheetView(sheet);

            assertThat(view.maxRow()).isEqualTo(4);
            assertThat(view.maxColumn()).isEqualTo(4);
            assertThat(view.cell(4, 4, true)).isSameAs(anchor);
            assertThat(view.text(4, 4)).isEqualTo("병합 값");
            assertThat(view.cell(4, 4, false)).isNull();
            assertThat(view.isInMultiColumnMergedRegion(2, 2)).isTrue();
            assertThat(view.isInMultiColumnMergedRegion(4, 4)).isTrue();
            assertThat(view.isInMultiColumnMergedRegion(1, 1)).isFalse();
        }
    }

    @Test
    void rejectsOneMergedRegionWhoseCoverageExceedsTheLimit() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("oversized");
            int lastRow = Math.toIntExact(SheetView.MAX_MERGED_REGION_CELLS);
            CellRangeAddress range = new CellRangeAddress(0, lastRow, 0, 0);
            sheet.addMergedRegion(range);

            CourseWorkbookParseException exception = catchThrowableOfType(
                    CourseWorkbookParseException.class,
                    () -> new SheetView(sheet)
            );

            assertThat(exception.code()).isEqualTo("WORKBOOK_TOO_LARGE");
            assertThat(exception.details())
                    .containsEntry("limitType", "mergedRegionCells")
                    .containsEntry("actual", SheetView.MAX_MERGED_REGION_CELLS + 1)
                    .containsEntry("max", SheetView.MAX_MERGED_REGION_CELLS)
                    .containsEntry("range", range.formatAsString());
        }
    }

    @Test
    void rejectsMergedRegionsWhoseCumulativeCoverageExceedsTheLimit() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("cumulative");
            int cellsPerRegion = Math.toIntExact(
                    SheetView.MAX_TOTAL_MERGED_REGION_CELLS / 2 + 1
            );
            sheet.addMergedRegion(new CellRangeAddress(0, cellsPerRegion - 1, 0, 0));
            sheet.addMergedRegion(new CellRangeAddress(0, cellsPerRegion - 1, 2, 2));

            CourseWorkbookParseException exception = catchThrowableOfType(
                    CourseWorkbookParseException.class,
                    () -> new SheetView(sheet)
            );

            assertThat(exception.code()).isEqualTo("WORKBOOK_TOO_LARGE");
            assertThat(exception.details())
                    .containsEntry("limitType", "totalMergedRegionCells")
                    .containsEntry("actual", (long) cellsPerRegion * 2)
                    .containsEntry("max", SheetView.MAX_TOTAL_MERGED_REGION_CELLS);
        }
    }
}
