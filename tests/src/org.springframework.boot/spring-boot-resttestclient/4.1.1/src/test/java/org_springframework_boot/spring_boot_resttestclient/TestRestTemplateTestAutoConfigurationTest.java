/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_resttestclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

public class TestRestTemplateTestAutoConfigurationTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void createsTestRestTemplateBoundToLocalServer() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0", "spring.main.banner-mode=off")
                .run()) {
            TestRestTemplate template = context.getBean(TestRestTemplate.class);

            ResponseEntity<String> response = template.getForEntity("/test-rest-template", String.class);

            assertThat(template.getRootUri()).startsWith("http://localhost:");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("created by test rest template auto-configuration");
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(HTTP_TIMEOUT);
        requestFactory.setReadTimeout(HTTP_TIMEOUT);
        return requestFactory;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @AutoConfigureTestRestTemplate
    public static class TestApplication {
        @Bean
        RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder()
                    .requestFactory(TestRestTemplateTestAutoConfigurationTest::requestFactory);
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    public static class TestController {
        @GetMapping("/test-rest-template")
        String testRestTemplate() {
            return "created by test rest template auto-configuration";
        }
    }
}
