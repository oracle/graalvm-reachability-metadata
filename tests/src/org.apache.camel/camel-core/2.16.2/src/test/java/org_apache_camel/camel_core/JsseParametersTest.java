/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.util.jsse.JsseParameters;
import org.junit.jupiter.api.Test;

public class JsseParametersTest {
    private static final byte[] RESOURCE_BYTES = "jsse-parameters-resource".getBytes(StandardCharsets.UTF_8);

    @Test
    void resolvesResourceFromThreadContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ResourceClassLoader());

            try (InputStream resource = new TestableJsseParameters().openResource("jsse-resource.txt")) {
                assertThat(resource).isNotNull();
                assertThat(resource.readAllBytes()).isEqualTo(RESOURCE_BYTES);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void resolvesResourceFromClassResourceLookupWhenThreadContextClassLoaderMisses() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new EmptyClassLoader());

            try (InputStream resource = new TestableJsseParameters().openResource("/META-INF/MANIFEST.MF")) {
                assertThat(resource).isNotNull();
                assertThat(resource.read()).isNotEqualTo(-1);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static final class TestableJsseParameters extends JsseParameters {
        private InputStream openResource(String resource) throws IOException {
            return resolveResource(resource);
        }
    }

    private static class EmptyClassLoader extends ClassLoader {
        EmptyClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return null;
        }
    }

    private static final class ResourceClassLoader extends EmptyClassLoader {
        @Override
        public InputStream getResourceAsStream(String name) {
            if ("jsse-resource.txt".equals(name)) {
                return new ByteArrayInputStream(RESOURCE_BYTES);
            }
            return null;
        }
    }
}
