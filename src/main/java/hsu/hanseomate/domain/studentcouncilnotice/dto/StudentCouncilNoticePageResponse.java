package hsu.hanseomate.domain.studentcouncilnotice.dto;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public record StudentCouncilNoticePageResponse(
        List<StudentCouncilNoticeListItemResponse> items,
        int page,
        int size,
        int totalPages,
        long totalElements,
        boolean hasNext
) {

    public static StudentCouncilNoticePageResponse from(Page<StudentCouncilNotice> noticePage) {
        return from(noticePage, Map.of(), Map.of());
    }

    public static StudentCouncilNoticePageResponse from(
            Page<StudentCouncilNotice> noticePage,
            Map<Long, List<StudentCouncilNoticeImageResponse>> imagesByNoticeId,
            Map<Long, List<StudentCouncilNoticeAttachmentResponse>> attachmentsByNoticeId
    ) {
        return new StudentCouncilNoticePageResponse(
                noticePage.getContent().stream()
                        .map(notice -> StudentCouncilNoticeListItemResponse.from(
                                notice,
                                imagesByNoticeId.getOrDefault(notice.getId(), List.of()),
                                attachmentsByNoticeId.getOrDefault(notice.getId(), List.of())
                        ))
                        .toList(),
                noticePage.getNumber(),
                noticePage.getSize(),
                noticePage.getTotalPages(),
                noticePage.getTotalElements(),
                noticePage.hasNext()
        );
    }
}
