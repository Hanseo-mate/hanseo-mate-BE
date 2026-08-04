package hsu.hanseomate.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class AdminJwtRequestPostProcessor {

    private AdminJwtRequestPostProcessor() {
    }

    public static RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(jwt -> jwt
                        .subject("admin-test-user")
                        .claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
