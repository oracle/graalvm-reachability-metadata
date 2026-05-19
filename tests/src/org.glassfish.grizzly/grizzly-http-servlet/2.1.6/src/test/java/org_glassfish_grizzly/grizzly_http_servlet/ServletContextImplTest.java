/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_servlet;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.glassfish.grizzly.servlet.ServletContextImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletContextImplTest {
    private static final String RESOURCE_PATH =
            "/org_glassfish_grizzly/grizzly_http_servlet/servlet-context-resource.txt";

    @Test
    void servletContextLoadsResourcesFromThreadContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ServletContextImplTest.class.getClassLoader());
        try {
            ServletContextImpl servletContext = new ServletContextImpl();

            URL resourceUrl = servletContext.getResource(RESOURCE_PATH);
            String resourceContents;
            try (InputStream input = servletContext.getResourceAsStream(RESOURCE_PATH)) {
                assertThat(input).isNotNull();
                resourceContents = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            assertThat(resourceUrl).isNotNull();
            assertThat(resourceUrl.toString()).endsWith("servlet-context-resource.txt");
            assertThat(resourceContents).contains("loaded through ServletContextImpl");
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
