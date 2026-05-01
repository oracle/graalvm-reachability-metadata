/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.ST4;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.stringtemplate.v4.STGroup;

import static org.assertj.core.api.Assertions.assertThat;

public class STGroupTest {
    private static final String TEMPLATE_RESOURCE = "org_antlr/ST4/stgroup-template.st";
    private static final String TEMPLATE_CONTENT = "Hello, <name>!\n";

    @Test
    void resolvesResourcesWithThreadContextClassLoader() throws Exception {
        final URL resource = withContextClassLoader(
                STGroupTest.class.getClassLoader(),
                () -> new STGroup().getURL(TEMPLATE_RESOURCE));

        assertThat(resource).isNotNull();
        assertThat(readResource(resource)).isEqualTo(TEMPLATE_CONTENT);
    }

    @Test
    void fallsBackToGroupClassLoaderWhenContextClassLoaderMisses() throws Exception {
        final URL resource = withContextClassLoader(
                new RejectingResourceClassLoader(),
                () -> new STGroup().getURL(TEMPLATE_RESOURCE));

        assertThat(resource).isNotNull();
        assertThat(readResource(resource)).isEqualTo(TEMPLATE_CONTENT);
    }

    private static <T> T withContextClassLoader(
            ClassLoader classLoader,
            ThrowingSupplier<T> supplier) throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader previousClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    private static String readResource(URL resource) throws IOException {
        try (InputStream inputStream = resource.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class RejectingResourceClassLoader extends ClassLoader {
        private RejectingResourceClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            return null;
        }
    }
}
