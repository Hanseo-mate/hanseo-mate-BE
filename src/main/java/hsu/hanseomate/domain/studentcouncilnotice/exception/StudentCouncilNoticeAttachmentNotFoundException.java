package hsu.hanseomate.domain.studentcouncilnotice.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;

public class StudentCouncilNoticeAttachmentNotFoundException
        extends ResourceNotFoundException {

    public StudentCouncilNoticeAttachmentNotFoundException(
            Long noticeId,
            Long attachmentId
    ) {
        super(
                "학생회 공지 첨부파일을 찾을 수 없습니다. noticeId="
                        + noticeId
                        + ", attachmentId="
                        + attachmentId
        );
    }
}
