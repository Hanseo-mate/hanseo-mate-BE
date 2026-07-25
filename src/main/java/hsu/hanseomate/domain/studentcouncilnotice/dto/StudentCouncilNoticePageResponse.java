package hsu.hanseomate.domain.studentcouncilnotice.dto;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import java.util.List;
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
        return new StudentCouncilNoticePageResponse(
                noticePage.getContent().stream()
                        .map(StudentCouncilNoticeListItemResponse::from)
                        .toList(),
                noticePage.getNumber(),
                noticePage.getSize(),
                noticePage.getTotalPages(),
                noticePage.getTotalElements(),
                noticePage.hasNext()
        );
    }
}
