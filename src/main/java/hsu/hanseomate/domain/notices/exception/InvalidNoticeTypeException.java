package hsu.hanseomate.domain.notices.exception;

import hsu.hanseomate.global.exception.BadRequestException;

public class InvalidNoticeTypeException extends BadRequestException {

    public InvalidNoticeTypeException() {
        super("지원하지 않는 공지 타입입니다. academic, general, scholarship, graduate 중에서 선택해 주세요.");
    }
}
