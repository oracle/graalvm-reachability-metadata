/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springdoc.springdoc_openapi_starter_webmvc_ui;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SpringDocConfigProperties.GroupConfig;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.core.providers.SpringWebProvider;
import org.springdoc.webmvc.ui.SwaggerConfigResource;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeWebMvc;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class Springdoc_openapi_starter_webmvc_uiTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void swaggerUiPropertiesLoadBundledUiVersionAndCopyConfiguredParameters() {
        SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();
        properties.setPath("/docs/swagger-ui.html");
        properties.setUrl("/internal/openapi");
        properties.setDeepLinking(true);
        properties.setDisplayOperationId(true);
        properties.setFilter("orders");
        properties.setTryItOutEnabled(true);
        properties.setPersistAuthorization(true);
        properties.setWithCredentials(true);

        properties.afterPropertiesSet();
        SwaggerUiConfigParameters parameters = new SwaggerUiConfigParameters(properties);
        Map<String, Object> configParameters = parameters.getConfigParameters();

        assertThat(properties.getVersion()).isNotBlank();
        assertThat(parameters.getPath()).isEqualTo("/docs/swagger-ui.html");
        assertThat(configParameters).containsEntry("url", "/internal/openapi")
                .containsEntry("deepLinking", true)
                .containsEntry("displayOperationId", true)
                .containsEntry("filter", "orders")
                .containsEntry("tryItOutEnabled", true)
                .containsEntry("persistAuthorization", true)
                .containsEntry("withCredentials", true);
    }

    @Test
    void welcomeRedirectBuildsSwaggerUiLocationFromServletContextAndPathPrefix() {
        SwaggerWelcomeWebMvc welcome = createWelcome("/api");
        MockHttpServletRequest request = request("/app", "/swagger-ui.html");
        request.addParameter("theme", "dark");
        request.addParameter("urls.primaryName", "public");

        ResponseEntity<Void> response = welcome.redirectToUi(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        URI location = response.getHeaders().getLocation();
        MultiValueMap<String, String> queryParameters = UriComponentsBuilder.fromUri(location).build().getQueryParams();
        assertThat(location.getPath()).isEqualTo("/app/swagger-ui/index.html");
        assertThat(queryParameters).containsEntry("theme", List.of("dark"))
                .containsEntry("urls.primaryName", List.of("public"));
    }

    @Test
    void swaggerConfigResourceReturnsResolvedOpenApiAndOAuthConfigurationUrls() {
        SwaggerWelcomeWebMvc welcome = createWelcome("/api");
        SwaggerConfigResource resource = new SwaggerConfigResource(welcome);
        MockHttpServletRequest request = request("/service", "/swagger-ui/swagger-config");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "api.example.test");
        request.addHeader("X-Forwarded-Port", "443");

        Map<String, Object> configuration = resource.openapiJson(request);

        assertThat(configuration).containsEntry("configUrl", "/service/api/v3/api-docs/swagger-config")
                .doesNotContainKey("url");
        assertThat(configuration.get("oauth2RedirectUrl").toString())
                .startsWith("https://api.example.test/service/swagger-ui/oauth2-redirect.html");
        assertThat(configuration.get("urls").toString()).contains("public-api")
                .contains("/service/api/v3/api-docs/public-api");
    }

    @Test
    void swaggerConfigResourceUsesExplicitOpenApiUrlWithoutConfiguredGroups() {
        SwaggerUiConfigProperties swaggerUiProperties = createSwaggerUiConfig();
        swaggerUiProperties.setUrl("/external/openapi.yaml");
        SwaggerWelcomeWebMvc welcome = new SwaggerWelcomeWebMvc(swaggerUiProperties, new SpringDocConfigProperties(),
                new FixedSpringWebProvider(""));
        SwaggerConfigResource resource = new SwaggerConfigResource(welcome);
        MockHttpServletRequest request = request("/service", "/swagger-ui/swagger-config");

        Map<String, Object> configuration = resource.openapiJson(request);

        assertThat(configuration).containsEntry("configUrl", "/service/v3/api-docs/swagger-config")
                .containsEntry("url", "/service/external/openapi.yaml")
                .doesNotContainKey("urls");
    }

    @Test
    void indexPageTransformerInjectsSwaggerUiAndOAuthConfiguration() throws IOException {
        SwaggerUiConfigProperties swaggerUiProperties = createSwaggerUiConfig();
        swaggerUiProperties.setConfigUrl("/gateway/swagger-config");
        swaggerUiProperties.setDocumentTitle("Orders API Documentation");
        swaggerUiProperties.setDisableSwaggerDefaultUrl(true);
        SwaggerUiOAuthProperties oauthProperties = new SwaggerUiOAuthProperties();
        oauthProperties.setClientId("orders-ui");
        oauthProperties.setAppName("Orders UI");
        SwaggerWelcomeWebMvc welcome = new SwaggerWelcomeWebMvc(swaggerUiProperties, createSpringDocConfig(),
                new FixedSpringWebProvider("/api"));
        SwaggerIndexPageTransformer transformer = new SwaggerIndexPageTransformer(swaggerUiProperties,
                oauthProperties, welcome, new ObjectMapperProvider(createSpringDocConfig()));
        Path initializer = temporaryDirectory.resolve("META-INF/resources/webjars/swagger-ui/")
                .resolve(swaggerUiProperties.getVersion()).resolve("swagger-initializer.js");
        Files.createDirectories(initializer.getParent());
        Files.writeString(initializer, """
                window.ui = SwaggerUIBundle({
                  url: "https://petstore.swagger.io/v2/swagger.json",
                  layout: "StandaloneLayout"
                  });
                """, StandardCharsets.UTF_8);
        MockHttpServletRequest request = request("/gateway", "/swagger-ui/swagger-initializer.js");

        Resource transformed = transformer.transform(request, new FileSystemResource(initializer), new NoOpChain());

        assertThat(transformed).isInstanceOf(TransformedResource.class);
        String content = transformed.getContentAsString(StandardCharsets.UTF_8);
        assertThat(content).contains("configUrl", "/gateway/swagger-config")
                .contains("displayOperationId", "true")
                .contains("ui.initOAuth")
                .contains("orders-ui")
                .contains("Orders UI")
                .doesNotContain("https://petstore.swagger.io/v2/swagger.json");
    }

    private static SwaggerWelcomeWebMvc createWelcome(String pathPrefix) {
        return new SwaggerWelcomeWebMvc(createSwaggerUiConfig(), createSpringDocConfig(),
                new FixedSpringWebProvider(pathPrefix));
    }

    private static SwaggerUiConfigProperties createSwaggerUiConfig() {
        SwaggerUiConfigProperties swaggerUiConfig = new SwaggerUiConfigProperties();
        swaggerUiConfig.setDeepLinking(true);
        swaggerUiConfig.setDisplayOperationId(true);
        swaggerUiConfig.setFilter("tag:orders");
        swaggerUiConfig.afterPropertiesSet();
        return swaggerUiConfig;
    }

    private static SpringDocConfigProperties createSpringDocConfig() {
        SpringDocConfigProperties properties = new SpringDocConfigProperties();
        properties.addGroupConfig(new GroupConfig("public-api", null, null, null, null, null, null, null,
                "Public API"));
        return properties;
    }

    private static MockHttpServletRequest request(String contextPath, String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", contextPath + servletPath);
        request.setContextPath(contextPath);
        request.setServletPath(servletPath);
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setScheme("http");
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    private static final class FixedSpringWebProvider implements ObjectProvider<SpringWebProvider> {

        private final SpringWebProvider springWebProvider;

        private FixedSpringWebProvider(String pathPrefix) {
            this.springWebProvider = new FixedPathPrefixSpringWebProvider(pathPrefix);
        }

        @Override
        public SpringWebProvider getObject(Object... args) throws BeansException {
            return springWebProvider;
        }

        @Override
        public SpringWebProvider getIfAvailable() throws BeansException {
            return springWebProvider;
        }

        @Override
        public SpringWebProvider getIfUnique() throws BeansException {
            return springWebProvider;
        }

        @Override
        public SpringWebProvider getObject() throws BeansException {
            return springWebProvider;
        }

        @Override
        public Iterator<SpringWebProvider> iterator() {
            return Stream.of(springWebProvider).iterator();
        }

        @Override
        public Stream<SpringWebProvider> stream() {
            return Stream.of(springWebProvider);
        }

        @Override
        public Stream<SpringWebProvider> orderedStream() {
            return stream();
        }
    }

    private static final class FixedPathPrefixSpringWebProvider extends SpringWebProvider {

        private final String pathPrefix;

        private FixedPathPrefixSpringWebProvider(String pathPrefix) {
            this.pathPrefix = pathPrefix;
        }

        @Override
        public Map<?, ?> getHandlerMethods() {
            return Map.of();
        }

        @Override
        public String findPathPrefix(SpringDocConfigProperties springDocConfigProperties) {
            return pathPrefix;
        }

        @Override
        public Set<String> getActivePatterns(Object requestMappingInfo) {
            return Set.of();
        }
    }

    private static final class NoOpChain implements ResourceTransformerChain {

        @Override
        public ResourceResolverChain getResolverChain() {
            return null;
        }

        @Override
        public Resource transform(HttpServletRequest request, Resource resource) {
            return resource;
        }
    }
}
