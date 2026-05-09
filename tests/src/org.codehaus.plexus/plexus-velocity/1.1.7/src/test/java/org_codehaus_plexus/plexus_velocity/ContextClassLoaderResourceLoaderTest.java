/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.codehaus.plexus.velocity.ContextClassLoaderResourceLoader;
import org.junit.jupiter.api.Test;

public class ContextClassLoaderResourceLoaderTest {
    private static final String TEMPLATE_RESOURCE = "templates/context-class-loader-resource-loader-template.vm";

    @Test
    void getResourceStreamLoadsTemplateFromThreadContextClassLoader() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        ClassLoader testClassLoader = ContextClassLoaderResourceLoaderTest.class.getClassLoader();
        currentThread.setContextClassLoader(testClassLoader);

        try {
            ContextClassLoaderResourceLoader loader = new ContextClassLoaderResourceLoader();

            try (InputStream stream = loader.getResourceStream(TEMPLATE_RESOURCE)) {
                assertThat(stream).isNotNull();
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("Hello from the context class loader resource");
            }
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }
}
