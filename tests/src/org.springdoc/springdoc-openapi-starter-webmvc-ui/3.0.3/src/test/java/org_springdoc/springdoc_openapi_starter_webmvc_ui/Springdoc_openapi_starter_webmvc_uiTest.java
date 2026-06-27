/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springdoc.springdoc_openapi_starter_webmvc_ui;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

public class Springdoc_openapi_starter_webmvc_uiTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Test
    void generatedOpenApiAndSwaggerUiAreServedByWebMvcApplication() throws Exception {
        try (ConfigurableApplicationContext context = SpringApplication.run(TestApplication.class,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--spring.main.banner-mode=off")) {
            int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(REQUEST_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()) {
                HttpResponse<String> greetingResponse = get(client, port,
                        "/greetings/springdoc?excited=true", "text/plain");
                assertThat(greetingResponse.statusCode()).isEqualTo(200);
                assertThat(greetingResponse.body()).isEqualTo("Hello, springdoc!");

                HttpResponse<String> apiDocsResponse = get(client, port, "/v3/api-docs", "application/json");
                assertThat(apiDocsResponse.statusCode()).isEqualTo(200);
                assertThat(apiDocsResponse.headers().firstValue("content-type")).hasValueSatisfying(contentType ->
                        assertThat(contentType).contains("application/json"));
                assertThat(apiDocsResponse.body())
                        .contains("\"openapi\"")
                        .contains("\"/greetings/{name}\"")
                        .contains("\"name\"")
                        .contains("\"excited\"");

                HttpResponse<String> swaggerConfigResponse = get(client, port,
                        "/v3/api-docs/swagger-config", "application/json");
                assertThat(swaggerConfigResponse.statusCode()).isEqualTo(200);
                assertThat(swaggerConfigResponse.body())
                        .contains("\"url\":\"/v3/api-docs\"")
                        .contains("\"configUrl\":\"/v3/api-docs/swagger-config\"");

                HttpResponse<String> swaggerUiResponse = get(client, port, "/swagger-ui.html", "text/html");
                assertThat(swaggerUiResponse.statusCode()).isEqualTo(200);
                assertThat(swaggerUiResponse.body())
                        .contains("Swagger UI")
                        .contains("swagger-initializer.js");

                HttpResponse<String> initializerResponse = get(client, port, "/swagger-ui/swagger-initializer.js",
                        "application/javascript");
                assertThat(initializerResponse.statusCode()).isEqualTo(200);
                assertThat(initializerResponse.body())
                        .contains("/v3/api-docs/swagger-config")
                        .contains("SwaggerUIBundle");
            }
        }
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path, String accept)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .header("Accept", accept)
                .timeout(REQUEST_TIMEOUT)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootApplication(proxyBeanMethods = false)
    @Import(GreetingController.class)
    public static class TestApplication {
    }

    @RestController
    public static class GreetingController {

        @GetMapping("/greetings/{name}")
        public String greeting(@PathVariable("name") String name,
                @RequestParam(name = "excited", defaultValue = "false") boolean excited) {
            String greeting = "Hello, " + name;
            if (excited) {
                return greeting + "!";
            }
            return greeting;
        }
    }
}
