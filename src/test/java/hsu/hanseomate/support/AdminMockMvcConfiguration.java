package hsu.hanseomate.support;

import static hsu.hanseomate.support.AdminJwtRequestPostProcessor.adminJwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class AdminMockMvcConfiguration {

    @Bean
    MockMvcBuilderCustomizer adminJwtDefaultRequestCustomizer() {
        return builder -> builder.defaultRequest(get("/").with(adminJwt()));
    }
}
