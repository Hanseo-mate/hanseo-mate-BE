package hsu.hanseomate.domain.notices.exception;

public class NoticeNotFoundException extends RuntimeException {

    public NoticeNotFoundException(Long noticeId) {
        super("해당 공지를 찾을 수 없습니다. noticeId=" + noticeId);
    }
}
