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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

public class Springdoc_openapi_starter_webmvc_uiTest {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private static ConfigurableApplicationContext applicationContext;
    private static HttpClient httpClient;
    private static URI baseUri;

    @BeforeAll
    static void startApplication() {
        SpringApplication application = new SpringApplication(TestApplication.class);
        application.setRegisterShutdownHook(false);
        applicationContext = application.run(
                "--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off");
        int port = applicationContext.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        baseUri = URI.create("http://127.0.0.1:" + port);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @AfterAll
    static void stopApplication() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } finally {
            if (applicationContext != null) {
                applicationContext.close();
            }
        }
    }

    @Test
    @Timeout(value = 59, unit = TimeUnit.SECONDS)
    void generatesAnnotatedOpenApiDocumentForMvcController() throws Exception {
        HttpResponse<String> greeting = get("/greetings/Ada?formal=true", MediaType.APPLICATION_JSON_VALUE);

        assertThat(greeting.statusCode()).isEqualTo(200);
        assertThat(contentType(greeting)).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(greeting.body())
                .contains("\"message\":\"Good day, Ada!\"", "\"recipient\":\"Ada\"", "\"formal\":true");

        HttpResponse<String> apiDocs = get("/v3/api-docs", MediaType.APPLICATION_JSON_VALUE);

        assertThat(apiDocs.statusCode()).isEqualTo(200);
        assertThat(contentType(apiDocs)).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(apiDocs.body())
                .contains(
                        "\"title\":\"Greeting API\"",
                        "\"/greetings/{name}\"",
                        "\"operationId\":\"greetByName\"",
                        "\"summary\":\"Create a personalized greeting\"",
                        "\"description\":\"Name of the recipient\"",
                        "\"description\":\"Greeting created\"",
                        "#/components/schemas/Greeting");
    }

    @Test
    @Timeout(value = 59, unit = TimeUnit.SECONDS)
    void servesSwaggerUiRedirectPageAssetsAndConfiguration() throws Exception {
        HttpResponse<String> redirect = get("/swagger-ui.html", MediaType.TEXT_HTML_VALUE);

        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location).contains("/swagger-ui/index.html"));

        HttpResponse<String> index = get("/swagger-ui/index.html", MediaType.TEXT_HTML_VALUE);

        assertThat(index.statusCode()).isEqualTo(200);
        assertThat(contentType(index)).startsWith(MediaType.TEXT_HTML_VALUE);
        assertThat(index.body()).contains("<title>Swagger UI</title>", "swagger-ui-bundle.js");

        HttpResponse<String> initializer = get("/swagger-ui/swagger-initializer.js", "application/javascript");

        assertThat(initializer.statusCode()).isEqualTo(200);
        assertThat(initializer.body()).contains("SwaggerUIBundle", "/v3/api-docs/swagger-config");

        HttpResponse<String> configuration = get("/v3/api-docs/swagger-config", MediaType.APPLICATION_JSON_VALUE);

        assertThat(configuration.statusCode()).isEqualTo(200);
        assertThat(contentType(configuration)).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(configuration.body())
                .contains("\"configUrl\"", "/v3/api-docs/swagger-config", "\"url\"", "/v3/api-docs");
    }

    private static HttpResponse<String> get(String path, String acceptedMediaType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", acceptedMediaType)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(GreetingController.class)
    @OpenAPIDefinition(
            info = @Info(
                    title = "Greeting API",
                    version = "current",
                    description = "API generated from an annotated Spring MVC controller"))
    public static class TestApplication {
    }

    @RestController
    @RequestMapping("/greetings")
    @Tag(name = "Greetings", description = "Personalized greeting operations")
    public static class GreetingController {
        @GetMapping(value = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
        @Operation(
                operationId = "greetByName",
                summary = "Create a personalized greeting",
                responses = @ApiResponse(
                        responseCode = "200",
                        description = "Greeting created",
                        content = @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = Greeting.class))))
        public Greeting greet(
                @Parameter(description = "Name of the recipient", example = "Ada") @PathVariable("name") String name,
                @Parameter(description = "Whether to use a formal salutation")
                        @RequestParam(name = "formal", defaultValue = "false") boolean formal) {
            String salutation = formal ? "Good day" : "Hello";
            return new Greeting(salutation + ", " + name + "!", name, formal);
        }
    }

    @Schema(name = "Greeting", description = "A personalized greeting")
    public record Greeting(
            @Schema(description = "Rendered greeting", example = "Hello, Ada!") String message,
            @Schema(description = "Greeting recipient", example = "Ada") String recipient,
            @Schema(description = "Whether the greeting is formal", example = "false") boolean formal) {
    }
}
