package hsu.hanseomate.domain.courseimport.parser.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

/** One-based worksheet accessor with merged-cell anchor resolution. */
public final class SheetView {

    private static final int MERGED_REGION_BUCKET_SIZE = 16;

    static final int MAX_MERGED_REGION_COUNT = 10_000;
    static final long MAX_MERGED_REGION_CELLS =
            CourseWorkbookParseEngine.DEFAULT_MAX_WORKBOOK_CELLS;
    static final long MAX_TOTAL_MERGED_REGION_CELLS =
            CourseWorkbookParseEngine.DEFAULT_MAX_WORKBOOK_CELLS;

    private final Sheet sheet;
    private final String name;
    private final int maxRow;
    private final int maxColumn;
    private final Map<Long, List<MergedRegion>> mergedRegionsByBucket;

    public SheetView(Sheet sheet) {
        this.sheet = sheet;
        this.name = sheet.getSheetName().trim().isEmpty() ? sheet.getSheetName() : sheet.getSheetName().trim();
        int detectedMaxRow = Math.max(1, sheet.getLastRowNum() + 1);
        int lastColumn = 0;
        for (Row row : sheet) {
            if (row.getLastCellNum() > lastColumn) {
                lastColumn = row.getLastCellNum();
            }
        }
        MergedRegionIndex mergedRegionIndex = indexMergedRegions(
                detectedMaxRow,
                Math.max(1, lastColumn)
        );
        this.maxRow = mergedRegionIndex.maxRow();
        this.maxColumn = mergedRegionIndex.maxColumn();
        this.mergedRegionsByBucket = mergedRegionIndex.regionsByBucket();
    }

    public Sheet sheet() {
        return sheet;
    }

    public String name() {
        return name;
    }

    public int maxRow() {
        return maxRow;
    }

    public int maxColumn() {
        return maxColumn;
    }

    public Cell cell(int rowNumber, int columnNumber, boolean resolveMerged) {
        Position position = resolveMerged
                ? mergedAnchor(rowNumber, columnNumber)
                : new Position(rowNumber, columnNumber);
        Row row = sheet.getRow(position.row() - 1);
        return row == null ? null : row.getCell(position.column() - 1);
    }

    public String text(int rowNumber, int columnNumber) {
        return text(rowNumber, columnNumber, false, true);
    }

    public String text(int rowNumber, int columnNumber, boolean identifier) {
        return text(rowNumber, columnNumber, identifier, true);
    }

    public String text(
            int rowNumber,
            int columnNumber,
            boolean identifier,
            boolean resolveMerged
    ) {
        if (columnNumber < 1) {
            return "";
        }
        return ExcelText.displayText(cell(rowNumber, columnNumber, resolveMerged), identifier);
    }

    public String sourceText(
            int rowNumber,
            int columnNumber,
            boolean identifier,
            boolean resolveMerged
    ) {
        if (columnNumber < 1) {
            return null;
        }
        return ExcelText.sourceText(cell(rowNumber, columnNumber, resolveMerged), identifier);
    }

    public List<String> rowValues(int rowNumber, boolean resolveMerged) {
        List<String> values = new ArrayList<>(maxColumn);
        for (int column = 1; column <= maxColumn; column++) {
            values.add(text(rowNumber, column, false, resolveMerged));
        }
        return values;
    }

    public List<String> meaningfulValues(int rowNumber, boolean resolveMerged) {
        return rowValues(rowNumber, resolveMerged).stream().filter(value -> !value.isEmpty()).toList();
    }

    public boolean isInMultiColumnMergedRegion(int rowNumber, int columnNumber) {
        return mergedRegions(rowNumber, columnNumber).stream().anyMatch(region ->
                region.contains(rowNumber, columnNumber)
                        && region.firstColumn() < region.lastColumn());
    }

    public int mergedColumnSpan(int rowNumber, int columnNumber) {
        List<MergedRegion> candidates = mergedRegions(rowNumber, columnNumber);
        for (int index = candidates.size() - 1; index >= 0; index--) {
            MergedRegion region = candidates.get(index);
            if (region.contains(rowNumber, columnNumber)) {
                return region.lastColumn() - region.firstColumn() + 1;
            }
        }
        return 1;
    }

    private MergedRegionIndex indexMergedRegions(int detectedMaxRow, int detectedMaxColumn) {
        List<CellRangeAddress> ranges = sheet.getMergedRegions();
        if (ranges.size() > MAX_MERGED_REGION_COUNT) {
            throw mergedRegionLimitExceeded(
                    "mergedRegionCount",
                    ranges.size(),
                    MAX_MERGED_REGION_COUNT,
                    null
            );
        }

        Map<Long, List<MergedRegion>> indexedByBucket = new HashMap<>();
        long totalCoverage = 0;
        int mergedMaxRow = detectedMaxRow;
        int mergedMaxColumn = detectedMaxColumn;
        for (CellRangeAddress range : ranges) {
            long rowCount = (long) range.getLastRow() - range.getFirstRow() + 1;
            long columnCount = (long) range.getLastColumn() - range.getFirstColumn() + 1;
            long coverage = rowCount * columnCount;
            if (coverage > MAX_MERGED_REGION_CELLS) {
                throw mergedRegionLimitExceeded(
                        "mergedRegionCells",
                        coverage,
                        MAX_MERGED_REGION_CELLS,
                        range
                );
            }
            if (totalCoverage > MAX_TOTAL_MERGED_REGION_CELLS - coverage) {
                throw mergedRegionLimitExceeded(
                        "totalMergedRegionCells",
                        totalCoverage + coverage,
                        MAX_TOTAL_MERGED_REGION_CELLS,
                        range
                );
            }
            totalCoverage += coverage;

            MergedRegion region = MergedRegion.from(range);
            int firstRowBucket = bucket(region.firstRow());
            int lastRowBucket = bucket(region.lastRow());
            int firstColumnBucket = bucket(region.firstColumn());
            int lastColumnBucket = bucket(region.lastColumn());
            for (int rowBucket = firstRowBucket; rowBucket <= lastRowBucket; rowBucket++) {
                for (int columnBucket = firstColumnBucket;
                     columnBucket <= lastColumnBucket;
                     columnBucket++) {
                    indexedByBucket.computeIfAbsent(
                            bucketKey(rowBucket, columnBucket),
                            ignored -> new ArrayList<>()
                    ).add(region);
                }
            }
            mergedMaxRow = Math.max(mergedMaxRow, region.lastRow());
            mergedMaxColumn = Math.max(mergedMaxColumn, region.lastColumn());
        }
        Map<Long, List<MergedRegion>> immutableByBucket = new HashMap<>(indexedByBucket.size());
        indexedByBucket.forEach((key, regions) ->
                immutableByBucket.put(key, List.copyOf(regions)));
        return new MergedRegionIndex(
                Map.copyOf(immutableByBucket),
                mergedMaxRow,
                mergedMaxColumn
        );
    }

    private Position mergedAnchor(int row, int column) {
        // Iterating backwards preserves the former map's last-region-wins behavior
        // for malformed workbooks containing overlapping merged ranges.
        List<MergedRegion> candidates = mergedRegions(row, column);
        for (int index = candidates.size() - 1; index >= 0; index--) {
            MergedRegion region = candidates.get(index);
            if (region.contains(row, column)) {
                return new Position(region.firstRow(), region.firstColumn());
            }
        }
        return new Position(row, column);
    }

    private List<MergedRegion> mergedRegions(int row, int column) {
        return mergedRegionsByBucket.getOrDefault(
                bucketKey(bucket(row), bucket(column)),
                List.of()
        );
    }

    private static int bucket(int oneBasedCoordinate) {
        return (oneBasedCoordinate - 1) / MERGED_REGION_BUCKET_SIZE;
    }

    private static long bucketKey(int rowBucket, int columnBucket) {
        return ((long) rowBucket << 32) | (columnBucket & 0xffffffffL);
    }

    private CourseWorkbookParseException mergedRegionLimitExceeded(
            String limitType,
            long actual,
            long maximum,
            CellRangeAddress range
    ) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("sheetName", name);
        details.put("limitType", limitType);
        details.put("actual", actual);
        details.put("max", maximum);
        if (range != null) {
            details.put("range", range.formatAsString());
        }
        return new CourseWorkbookParseException(
                "WORKBOOK_TOO_LARGE",
                "병합 셀 범위가 허용 범위를 초과했습니다.",
                details
        );
    }

    private record Position(int row, int column) {
    }

    private record MergedRegion(
            int firstRow,
            int lastRow,
            int firstColumn,
            int lastColumn
    ) {
        static MergedRegion from(CellRangeAddress range) {
            return new MergedRegion(
                    range.getFirstRow() + 1,
                    range.getLastRow() + 1,
                    range.getFirstColumn() + 1,
                    range.getLastColumn() + 1
            );
        }

        boolean contains(int row, int column) {
            return row >= firstRow && row <= lastRow
                    && column >= firstColumn && column <= lastColumn;
        }
    }

    private record MergedRegionIndex(
            Map<Long, List<MergedRegion>> regionsByBucket,
            int maxRow,
            int maxColumn
    ) {
    }
}
