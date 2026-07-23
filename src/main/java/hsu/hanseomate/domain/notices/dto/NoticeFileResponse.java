package hsu.hanseomate.domain.notices.dto;

import hsu.hanseomate.domain.notices.entity.NoticeFile;

public record NoticeFileResponse(
        Long id,
        String fileName,
        String fileUrl
) {

    public static NoticeFileResponse from(NoticeFile noticeFile) {
        return new NoticeFileResponse(
                noticeFile.getId(),
                noticeFile.getFileName(),
                noticeFile.getFileUrl()
        );
    }
}
