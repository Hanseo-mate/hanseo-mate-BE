package hsu.hanseomate.domain.appupdate.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.updates")
public class AppUpdateProperties {
    // 미설정 시 iOS 정책 저장/게시만 거부하며, 공개 조회와 Android 운영은 계속 가능하다.
    @Pattern(regexp = "^$|[1-9][0-9]*")
    private String iosAppStoreId = "";

    @NotBlank
    @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")
    private String androidPackageId = "com.hanseomate.app";

    @Min(1)
    private int checkRequestsPerMinute = 600;

    @Min(1)
    private int rateLimitMaxIps = 10000;
}
