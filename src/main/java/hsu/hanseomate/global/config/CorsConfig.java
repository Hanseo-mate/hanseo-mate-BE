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

        CorsConfiguration authenticatedUserConfiguration = new CorsConfiguration();
        authenticatedUserConfiguration.setAllowedOrigins(corsProperties.allowedOrigins());
        authenticatedUserConfiguration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        authenticatedUserConfiguration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT
        ));
        authenticatedUserConfiguration.setAllowCredentials(false);
        authenticatedUserConfiguration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        CorsConfiguration publicNoticeConfiguration = new CorsConfiguration();
        publicNoticeConfiguration.setAllowedOrigins(corsProperties.allowedOrigins());
        publicNoticeConfiguration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.OPTIONS.name()
        ));
        publicNoticeConfiguration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT
        ));
        publicNoticeConfiguration.setExposedHeaders(List.of(
                HttpHeaders.CONTENT_DISPOSITION,
                HttpHeaders.CONTENT_LENGTH,
                "X-Content-Type-Options"
        ));
        publicNoticeConfiguration.setAllowCredentials(false);
        publicNoticeConfiguration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        CorsConfiguration publicReadConfiguration = new CorsConfiguration();
        publicReadConfiguration.setAllowedOrigins(corsProperties.allowedOrigins());
        publicReadConfiguration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.OPTIONS.name()
        ));
        publicReadConfiguration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT
        ));
        publicReadConfiguration.setAllowCredentials(false);
        publicReadConfiguration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        CorsConfiguration gradeCalculatorConfiguration = new CorsConfiguration();
        gradeCalculatorConfiguration.setAllowedOrigins(corsProperties.allowedOrigins());
        gradeCalculatorConfiguration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.OPTIONS.name()
        ));
        gradeCalculatorConfiguration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT
        ));
        gradeCalculatorConfiguration.setAllowCredentials(false);
        gradeCalculatorConfiguration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

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
        source.registerCorsConfiguration(
                "/api/auth/me",
                authenticatedUserConfiguration
        );
        source.registerCorsConfiguration(
                "/api/auth/me/**",
                authenticatedUserConfiguration
        );
        source.registerCorsConfiguration(
                "/api/notices/**",
                publicNoticeConfiguration
        );
        source.registerCorsConfiguration(
                "/api/system-notices",
                publicReadConfiguration
        );
        source.registerCorsConfiguration(
                "/api/system-notices/**",
                publicReadConfiguration
        );
        source.registerCorsConfiguration("/api/home", publicReadConfiguration);
        source.registerCorsConfiguration(
                "/api/bus-schedules",
                publicReadConfiguration
        );
        source.registerCorsConfiguration(
                "/api/bus-schedules/**",
                publicReadConfiguration
        );
        source.registerCorsConfiguration(
                "/api/timetables/today-locations",
                publicReadConfiguration
        );
        source.registerCorsConfiguration(
                "/api/cafeteria/**",
                publicReadConfiguration
        );
        source.registerCorsConfiguration(
                "/api/grade-calculations",
                gradeCalculatorConfiguration
        );
        source.registerCorsConfiguration(
                "/api/grade-calculations/**",
                gradeCalculatorConfiguration
        );
        return source;
    }
}
