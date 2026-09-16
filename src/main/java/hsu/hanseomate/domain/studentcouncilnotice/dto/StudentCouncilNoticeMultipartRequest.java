package hsu.hanseomate.domain.studentcouncilnotice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StudentCouncilNoticeMultipartRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 500, message = "제목은 500자 이하여야 합니다.")
        String title,

        @NotBlank(message = "작성자는 필수입니다.")
        @Size(max = 100, message = "작성자는 100자 이하여야 합니다.")
        String author,

        @NotBlank(message = "내용은 필수입니다.")
        String content,

        List<@Positive(message = "유지할 이미지 ID는 1 이상이어야 합니다.") Long>
        retainedImageIds,

        List<@Positive(message = "유지할 첨부파일 ID는 1 이상이어야 합니다.") Long>
        retainedAttachmentIds
) {

    public StudentCouncilNoticeMultipartRequest {
        retainedImageIds = retainedImageIds == null ? null : List.copyOf(retainedImageIds);
        retainedAttachmentIds = retainedAttachmentIds == null
                ? null
                : List.copyOf(retainedAttachmentIds);
    }
}
