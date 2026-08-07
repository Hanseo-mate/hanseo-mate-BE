package hsu.hanseomate.global.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600L;

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            CorsProperties corsProperties
    ) {
        CorsConfiguration adminConfiguration = new CorsConfiguration();
        adminConfiguration.setAllowedOrigins(corsProperties.allowedOrigins());
        adminConfiguration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        adminConfiguration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT
        ));
        adminConfiguration.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        adminConfiguration.setAllowCredentials(false);
        adminConfiguration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        CorsConfiguration loginConfiguration = new CorsConfiguration();
        loginConfiguration.setAllowedOrigins(corsProperties.allowedOrigins());
        loginConfiguration.setAllowedMethods(List.of(
                HttpMethod.POST.name(),
                HttpMethod.OPTIONS.name()
        ));
        loginConfiguration.setAllowedHeaders(List.of(
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT
        ));
        loginConfiguration.setAllowCredentials(false);
        loginConfiguration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", adminConfiguration);
        source.registerCorsConfiguration(
                "/api/v1/timetables/major",
                adminConfiguration
        );
        source.registerCorsConfiguration(
                "/api/v1/timetables/general-education",
                adminConfiguration
        );
        source.registerCorsConfiguration("/api/auth/login", loginConfiguration);
        return source;
    }
}
