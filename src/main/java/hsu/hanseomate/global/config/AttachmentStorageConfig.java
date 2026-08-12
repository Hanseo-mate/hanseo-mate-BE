package hsu.hanseomate.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AttachmentStorageProperties.class)
public class AttachmentStorageConfig {
}
