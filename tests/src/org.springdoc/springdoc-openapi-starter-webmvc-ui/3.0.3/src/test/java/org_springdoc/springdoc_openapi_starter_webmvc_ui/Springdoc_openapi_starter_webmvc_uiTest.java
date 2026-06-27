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
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Test
    void groupedOpenApiDefinitionsAreExposedSeparatelyAndAddedToSwaggerUiConfig() throws Exception {
        try (ConfigurableApplicationContext context = SpringApplication.run(GroupedOpenApiApplication.class,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--spring.main.banner-mode=off")) {
            int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(REQUEST_TIMEOUT)
                    .build()) {
                HttpResponse<String> publicApiDocsResponse = get(client, port, "/v3/api-docs/public",
                        "application/json");
                assertThat(publicApiDocsResponse.statusCode()).isEqualTo(200);
                assertThat(publicApiDocsResponse.body())
                        .contains("\"/public/ping\"")
                        .doesNotContain("\"/admin/ping\"");

                HttpResponse<String> adminApiDocsResponse = get(client, port, "/v3/api-docs/admin",
                        "application/json");
                assertThat(adminApiDocsResponse.statusCode()).isEqualTo(200);
                assertThat(adminApiDocsResponse.body())
                        .contains("\"/admin/ping\"")
                        .doesNotContain("\"/public/ping\"");

                HttpResponse<String> swaggerConfigResponse = get(client, port,
                        "/v3/api-docs/swagger-config", "application/json");
                assertThat(swaggerConfigResponse.statusCode()).isEqualTo(200);
                assertThat(swaggerConfigResponse.body())
                        .contains("\"url\":\"/v3/api-docs/public\"")
                        .contains("\"name\":\"public\"")
                        .contains("\"url\":\"/v3/api-docs/admin\"")
                        .contains("\"name\":\"admin\"");
            }
        }
    }

    @Test
    void customApiDocsAndSwaggerUiPathsAreHonored() throws Exception {
        try (ConfigurableApplicationContext context = SpringApplication.run(TestApplication.class,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--springdoc.api-docs.path=/openapi",
                "--springdoc.swagger-ui.path=/docs")) {
            int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(REQUEST_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()) {
                HttpResponse<String> apiDocsResponse = get(client, port, "/openapi", "application/json");
                assertThat(apiDocsResponse.statusCode()).isEqualTo(200);
                assertThat(apiDocsResponse.body())
                        .contains("\"openapi\"")
                        .contains("\"/greetings/{name}\"");

                HttpResponse<String> swaggerConfigResponse = get(client, port,
                        "/openapi/swagger-config", "application/json");
                assertThat(swaggerConfigResponse.statusCode()).isEqualTo(200);
                assertThat(swaggerConfigResponse.body())
                        .contains("\"url\":\"/openapi\"")
                        .contains("\"configUrl\":\"/openapi/swagger-config\"");

                HttpResponse<String> customSwaggerUiResponse = get(client, port, "/docs", "text/html");
                assertThat(customSwaggerUiResponse.statusCode()).isEqualTo(200);
                assertThat(customSwaggerUiResponse.body())
                        .contains("Swagger UI")
                        .contains("swagger-initializer.js");

                HttpResponse<String> initializerResponse = get(client, port, "/swagger-ui/swagger-initializer.js",
                        "application/javascript");
                assertThat(initializerResponse.statusCode()).isEqualTo(200);
                assertThat(initializerResponse.body())
                        .contains("/openapi/swagger-config");
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

    @SpringBootApplication(proxyBeanMethods = false,
            scanBasePackages = "org_springdoc.springdoc_openapi_starter_webmvc_ui.none")
    @Import(GreetingController.class)
    public static class TestApplication {
    }

    @SpringBootApplication(proxyBeanMethods = false,
            scanBasePackages = "org_springdoc.springdoc_openapi_starter_webmvc_ui.none")
    @Import({PublicController.class, AdminController.class, GroupedOpenApiConfiguration.class})
    public static class GroupedOpenApiApplication {
    }

    @Configuration(proxyBeanMethods = false)
    public static class GroupedOpenApiConfiguration {

        @Bean
        public GroupedOpenApi publicOpenApi() {
            return GroupedOpenApi.builder()
                    .group("public")
                    .pathsToMatch("/public/**")
                    .build();
        }

        @Bean
        public GroupedOpenApi adminOpenApi() {
            return GroupedOpenApi.builder()
                    .group("admin")
                    .pathsToMatch("/admin/**")
                    .build();
        }
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

    @RestController
    public static class PublicController {

        @GetMapping("/public/ping")
        public String ping() {
            return "public";
        }
    }

    @RestController
    public static class AdminController {

        @GetMapping("/admin/ping")
        public String ping() {
            return "admin";
        }
    }
}
