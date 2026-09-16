package hsu.hanseomate.domain.studentcouncilnotice.dto;

import org.springframework.core.io.Resource;

public record StudentCouncilNoticeAttachmentDownload(
        Resource resource,
        String fileName,
        long fileSize
) {
}
