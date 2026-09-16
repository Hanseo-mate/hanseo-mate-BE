package hsu.hanseomate.domain.systemnotice.controller;

import hsu.hanseomate.domain.systemnotice.dto.SystemNoticeResponse;
import hsu.hanseomate.domain.systemnotice.service.SystemNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "시스템 공지 조회",
        description = "로그인 없이 시스템 공지 전체를 조회합니다."
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system-notices")
public class SystemNoticeController {

    private final SystemNoticeService systemNoticeService;

    @Operation(
            summary = "시스템 공지 전체 조회",
            description = "제목과 내용을 포함한 모든 시스템 공지를 최신 작성순으로 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public List<SystemNoticeResponse> getNotices() {
        return systemNoticeService.getNotices();
    }
}
