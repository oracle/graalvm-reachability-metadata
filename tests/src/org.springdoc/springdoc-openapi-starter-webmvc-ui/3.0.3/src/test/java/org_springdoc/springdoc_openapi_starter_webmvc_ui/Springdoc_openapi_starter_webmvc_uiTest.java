/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springdoc.springdoc_openapi_starter_webmvc_ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

public class Springdoc_openapi_starter_webmvc_uiTest {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static ConfigurableApplicationContext applicationContext;
    private static HttpClient httpClient;
    private static String baseUrl;

    @BeforeAll
    static void startApplication() {
        applicationContext = SpringApplication.run(TestApplication.class, "--server.port=0");
        int port = applicationContext.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        baseUrl = "http://localhost:" + port;
        httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    }

    @AfterAll
    static void stopApplication() {
        applicationContext.close();
    }

    @Test
    void exposesOpenApiDocumentationAndSwaggerUiForWebMvcEndpoint() throws Exception {
        HttpResponse<String> apiDocs = get("/v3/api-docs");
        assertThat(apiDocs.statusCode()).isEqualTo(200);
        assertThat(apiDocs.headers().firstValue("content-type")).hasValueSatisfying(
                contentType -> assertThat(contentType).contains("application/json"));
        assertThat(apiDocs.body()).contains("\"openapi\"");
        assertThat(apiDocs.body()).contains("\"/greeting\"");
        assertThat(apiDocs.body()).contains("\"get\"");

        HttpResponse<String> swaggerUi = get("/swagger-ui/index.html");
        assertThat(swaggerUi.statusCode()).isEqualTo(200);
        assertThat(swaggerUi.headers().firstValue("content-type")).hasValueSatisfying(
                contentType -> assertThat(contentType).contains("text/html"));
        assertThat(swaggerUi.body()).contains("Swagger UI");
        assertThat(swaggerUi.body()).contains("swagger-ui");
    }

    @Test
    void exposesSwaggerUiConfigurationForDefaultOpenApiDocument() throws Exception {
        HttpResponse<String> swaggerConfiguration = get("/v3/api-docs/swagger-config");

        assertThat(swaggerConfiguration.statusCode()).isEqualTo(200);
        assertThat(swaggerConfiguration.headers().firstValue("content-type")).hasValueSatisfying(
                contentType -> assertThat(contentType).contains("application/json"));
        assertThat(swaggerConfiguration.body()).contains("\"url\":\"/v3/api-docs\"");
        assertThat(swaggerConfiguration.body()).contains("\"configUrl\":\"/v3/api-docs/swagger-config\"");
    }

    @Test
    void redirectsLegacySwaggerUiPathToIndexPage() throws Exception {
        HttpResponse<String> response = get("/swagger-ui.html");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location")).hasValueSatisfying(
                location -> assertThat(location).contains("/swagger-ui/index.html"));
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootApplication
    static class TestApplication {
    }

    @RestController
    static class GreetingController {
        @GetMapping(value = "/greeting", produces = MediaType.TEXT_PLAIN_VALUE)
        String greeting() {
            return "Hello from Springdoc";
        }
    }
}
