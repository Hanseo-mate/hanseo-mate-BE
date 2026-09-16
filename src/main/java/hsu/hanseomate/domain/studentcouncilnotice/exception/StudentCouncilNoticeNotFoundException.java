package hsu.hanseomate.domain.studentcouncilnotice.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;

public class StudentCouncilNoticeNotFoundException extends ResourceNotFoundException {

    public StudentCouncilNoticeNotFoundException(Long noticeId) {
        super("학생회 공지를 찾을 수 없습니다. noticeId=" + noticeId);
    }
}
