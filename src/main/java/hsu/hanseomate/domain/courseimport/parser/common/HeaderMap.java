package hsu.hanseomate.domain.courseimport.parser.common;

import java.util.Map;

public record HeaderMap(
        int startRow,
        int rowNumber,
        Map<String, Integer> columns,
        Map<Integer, String> sourceHeaders
) {
    public Integer get(String key) {
        return columns.get(key);
    }
}
