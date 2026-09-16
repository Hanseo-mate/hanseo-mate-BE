package hsu.hanseomate.domain.systemnotice.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;

public class SystemNoticeNotFoundException extends ResourceNotFoundException {

    public SystemNoticeNotFoundException(Long noticeId) {
        super("시스템 공지를 찾을 수 없습니다. noticeId=" + noticeId);
    }
}
