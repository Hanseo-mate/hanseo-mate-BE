package hsu.hanseomate.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.attachment-storage")
public record AttachmentStorageProperties(
        String directory
) {
}
