/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springdoc.springdoc_openapi_starter_webmvc_ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.webmvc.ui.SwaggerResourceResolver;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceResolverChain;

public class Springdoc_openapi_starter_webmvc_uiTest {
    @Test
    void resolvesVersionedSwaggerUiWebJarResource() {
        SwaggerResourceResolver resolver = new SwaggerResourceResolver(new SwaggerUiConfigProperties());

        String resolvedPath = resolver.resolveUrlPath(
                "swagger-ui/swagger-ui.css", List.of(), new VersionedPathResolverChain());

        assertThat(resolvedPath)
                .startsWith("swagger-ui/")
                .endsWith("/swagger-ui.css")
                .isNotEqualTo("swagger-ui/swagger-ui.css");
    }

    private static final class VersionedPathResolverChain implements ResourceResolverChain {
        @Override
        public Resource resolveResource(
                HttpServletRequest request, String requestPath, List<? extends Resource> locations) {
            return null;
        }

        @Override
        public String resolveUrlPath(String resourcePath, List<? extends Resource> locations) {
            return resourcePath.equals("swagger-ui/swagger-ui.css") ? null : resourcePath;
        }
    }
}
