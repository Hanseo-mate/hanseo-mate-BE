package hsu.hanseomate.domain.notices.entity;

import hsu.hanseomate.domain.notices.exception.InvalidNoticeTypeException;
import java.util.Arrays;

public enum NoticeType {
    ACADEMIC("academic", "학사공지"),
    GENERAL("general", "일반공지"),
    SCHOLARSHIP("scholarship", "장학공지"),
    GRADUATE("graduate", "대학원공지");

    private final String value;
    private final String displayName;

    NoticeType(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public String value() {
        return value;
    }

    /** 알림 제목 등 사용자에게 노출되는 한국어 표시명을 반환합니다. */
    public String displayName() {
        return displayName;
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
