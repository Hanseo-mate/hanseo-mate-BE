package hsu.hanseomate.domain.courseimport.parser.common;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.DataFormatter;

/** Text conversion rules shared by every timetable parser. */
public final class ExcelText {

    private static final Pattern ZERO_FORMAT = Pattern.compile("^0+$");
    private static final Pattern CONTROL_WHITESPACE = Pattern.compile("[\\t\\r\\n]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_COMPACT = Pattern.compile("[^0-9A-Za-z가-힣]");
    private static final ThreadLocal<DataFormatter> FORMATTER = ThreadLocal.withInitial(
            () -> new DataFormatter(Locale.KOREA, true)
    );

    private ExcelText() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00a0', ' ')
                .replace('\u3000', ' ');
        normalized = CONTROL_WHITESPACE.matcher(normalized).replaceAll(" ");
        return WHITESPACE.matcher(normalized).replaceAll(" ").trim();
    }

    public static String compact(String value) {
        return NON_COMPACT.matcher(normalize(value)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public static String displayText(Cell cell, boolean identifier) {
        String source = sourceText(cell, identifier);
        return normalize(source);
    }

    /**
     * Returns the source cell text with string whitespace and line breaks intact.
     * Numeric identifiers still honor a pure-zero number format so visible
     * leading zeroes are not lost.
     */
    public static String sourceText(Cell cell, boolean identifier) {
        if (cell == null) {
            return null;
        }
        CellType type = effectiveType(cell);
        return switch (type) {
            case BLANK, _NONE, ERROR -> null;
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case STRING -> nonBlankOrNull(cell.getStringCellValue());
            case NUMERIC -> numericText(cell, identifier);
            case FORMULA -> nonBlankOrNull(FORMATTER.get().formatCellValue(cell));
        };
    }

    private static CellType effectiveType(Cell cell) {
        return cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
    }

    private static String numericText(Cell cell, boolean identifier) {
        if (DateUtil.isCellDateFormatted(cell)) {
            try {
                LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
                if (dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                    return dateTime.toLocalDate().toString();
                }
                if (dateTime.toLocalDate().equals(LocalDate.of(1899, 12, 31))
                        || dateTime.toLocalDate().equals(LocalDate.of(1900, 1, 1))) {
                    return dateTime.toLocalTime().toString();
                }
                return dateTime.toString();
            } catch (RuntimeException ignored) {
                return FORMATTER.get().formatCellValue(cell);
            }
        }

        double numeric = cell.getNumericCellValue();
        if (!Double.isFinite(numeric)) {
            return null;
        }
        BigDecimal decimal = BigDecimal.valueOf(numeric).stripTrailingZeros();
        String plain = decimal.toPlainString();
        if (identifier && decimal.scale() <= 0) {
            String format = cell.getCellStyle() == null ? "" : cell.getCellStyle().getDataFormatString();
            format = format == null ? "" : format.split(";", 2)[0]
                    .replace("\\\\", "")
                    .replace("\"", "");
            if (ZERO_FORMAT.matcher(format).matches()) {
                boolean negative = plain.startsWith("-");
                String digits = negative ? plain.substring(1) : plain;
                String padded = "0".repeat(Math.max(0, format.length() - digits.length())) + digits;
                return negative ? "-" + padded : padded;
            }
        }
        return plain;
    }

    private static String nonBlankOrNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
