/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_servlet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.glassfish.grizzly.servlet.ServletContextImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletContextImplTest {
    private static final String RESOURCE_PATH = "/org_glassfish_grizzly/grizzly_http_servlet/context-resource.txt";

    @Test
    void getResourceResolvesClasspathResourceThroughContextClassLoader() throws Exception {
        ServletContextImpl context = new ServletContextImpl();

        URL resource = context.getResource(RESOURCE_PATH);

        assertThat(resource).isNotNull();
        assertThat(resource.toExternalForm()).endsWith("context-resource.txt");
    }

    @Test
    void getResourceAsStreamReadsClasspathResourceThroughContextClassLoader() throws IOException {
        ServletContextImpl context = new ServletContextImpl();

        try (InputStream input = context.getResourceAsStream(RESOURCE_PATH)) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("grizzly servlet context resource\n");
        }
    }
}
