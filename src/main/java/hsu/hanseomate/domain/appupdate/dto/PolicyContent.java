package hsu.hanseomate.domain.appupdate.dto;

public record PolicyContent(
        String latestVersion, long latestBuild, boolean forceUpdateEnabled,
        Long minimumSupportedBuild, boolean optionalUpdateEnabled,
        String storeUrl, String title, String message
) {}
