package hsu.hanseomate.domain.appupdate.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import hsu.hanseomate.domain.appsetting.dto.StrictBooleanDeserializer;
import hsu.hanseomate.domain.appupdate.type.AppPlatform;
import hsu.hanseomate.domain.appupdate.type.PublishMode;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class AppUpdateRequests {
    private AppUpdateRequests() {}

    public interface StrictFields {
        @JsonAnySetter
        default void rejectUnknown(String field, JsonNode value) {
            throw new IllegalArgumentException("지원하지 않는 요청 필드입니다: " + field);
        }
    }

    public interface ContentInput {
        String latestVersion();
        Long latestBuild();
        Boolean forceUpdateEnabled();
        Long minimumSupportedBuild();
        Boolean optionalUpdateEnabled();
        String storeUrl();
        String title();
        String message();
        String reason();
    }

    @Schema(name = "AppUpdatePolicyCreateRequest")
    public record Create(
            @JsonDeserialize(using = StrictAppPlatformDeserializer.class) AppPlatform platform,
            @JsonDeserialize(using = StrictStringDeserializer.class) String latestVersion,
            @JsonDeserialize(using = StrictLongDeserializer.class) Long latestBuild,
            @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean forceUpdateEnabled,
            @JsonDeserialize(using = StrictLongDeserializer.class) Long minimumSupportedBuild,
            @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean optionalUpdateEnabled,
            @JsonDeserialize(using = StrictStringDeserializer.class) String storeUrl,
            @JsonDeserialize(using = StrictStringDeserializer.class) String title,
            @JsonDeserialize(using = StrictStringDeserializer.class) String message,
            @JsonDeserialize(using = StrictStringDeserializer.class) String reason
    ) implements StrictFields, ContentInput {}

    @Schema(name = "AppUpdatePolicyUpdateRequest")
    public record Update(
            @JsonDeserialize(using = StrictLongDeserializer.class) Long revision,
            @JsonDeserialize(using = StrictStringDeserializer.class) String latestVersion,
            @JsonDeserialize(using = StrictLongDeserializer.class) Long latestBuild,
            @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean forceUpdateEnabled,
            @JsonDeserialize(using = StrictLongDeserializer.class) Long minimumSupportedBuild,
            @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean optionalUpdateEnabled,
            @JsonDeserialize(using = StrictStringDeserializer.class) String storeUrl,
            @JsonDeserialize(using = StrictStringDeserializer.class) String title,
            @JsonDeserialize(using = StrictStringDeserializer.class) String message,
            @JsonDeserialize(using = StrictStringDeserializer.class) String reason
    ) implements StrictFields, ContentInput {}

    @Schema(name = "AppUpdatePolicyPublishRequest")
    public record Publish(
            @JsonDeserialize(using = StrictLongDeserializer.class) Long revision,
            @JsonDeserialize(using = StrictPublishModeDeserializer.class) PublishMode publishMode,
            @JsonDeserialize(using = StrictUtcInstantDeserializer.class) Instant effectiveAt,
            @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean storeAvailabilityConfirmed,
            @JsonDeserialize(using = StrictStringDeserializer.class) String reason
    ) implements StrictFields {}

    @Schema(name = "AppUpdatePolicyCancelRequest")
    public record Cancel(
            @JsonDeserialize(using = StrictLongDeserializer.class) Long revision,
            @JsonDeserialize(using = StrictStringDeserializer.class) String reason
    ) implements StrictFields {}

    @Schema(name = "AppUpdatePolicyRollbackRequest")
    public record Rollback(
            @JsonDeserialize(using = StrictLongDeserializer.class) Long expectedActivePolicyId,
            @JsonDeserialize(using = StrictLongDeserializer.class) Long expectedActiveRevision,
            @JsonDeserialize(using = StrictStringDeserializer.class) String reason
    ) implements StrictFields {}

    @Schema(name = "AppUpdatePolicyRelaxRequest")
    public record Relax(
            @JsonDeserialize(using = StrictLongDeserializer.class) Long revision,
            @JsonDeserialize(using = StrictLongDeserializer.class) Long expectedActivePolicyId,
            @JsonDeserialize(using = StrictLongDeserializer.class) Long expectedActiveRevision,
            @JsonDeserialize(using = StrictBooleanDeserializer.class) Boolean storeAvailabilityConfirmed,
            @JsonDeserialize(using = StrictStringDeserializer.class) String reason
    ) implements StrictFields {}

    @Schema(name = "AppUpdatePolicyPreviewRequest")
    public record Preview(
            @JsonDeserialize(using = StrictLongDeserializer.class) Long revision,
            @JsonDeserialize(contentUsing = StrictLongDeserializer.class) List<Long> builds
    ) implements StrictFields {}
}
