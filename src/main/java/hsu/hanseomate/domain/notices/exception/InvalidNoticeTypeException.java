package hsu.hanseomate.domain.notices.exception;

public class InvalidNoticeTypeException extends RuntimeException {

    public InvalidNoticeTypeException() {
        super("지원하지 않는 공지 타입입니다. academic, general, scholarship, graduate 중에서 선택해 주세요.");
    }
}
