package hsu.hanseomate.domain.notices.entity;

import hsu.hanseomate.domain.notices.exception.InvalidNoticeTypeException;
import java.util.Arrays;

public enum NoticeType {
    ACADEMIC("academic"),
    GENERAL("general"),
    SCHOLARSHIP("scholarship"),
    GRADUATE("graduate");

    private final String value;

    NoticeType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static NoticeType from(String rawNoticeType) {
        if (rawNoticeType == null) {
            throw new InvalidNoticeTypeException();
        }

        String normalized = rawNoticeType.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(noticeType -> noticeType.value.equals(normalized))
                .findFirst()
                .orElseThrow(InvalidNoticeTypeException::new);
    }
}
