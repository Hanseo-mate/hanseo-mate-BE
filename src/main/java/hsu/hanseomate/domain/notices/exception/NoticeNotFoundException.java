package hsu.hanseomate.domain.notices.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;

public class NoticeNotFoundException extends ResourceNotFoundException {

    public NoticeNotFoundException(Long noticeId) {
        super("해당 공지를 찾을 수 없습니다. noticeId=" + noticeId);
    }
}
