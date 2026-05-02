/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.github.javaparser.Provider;
import com.github.javaparser.Providers;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class ProvidersTest {
    private static final String RESOURCE_PATH = "examples/provider-resource.java";
    private static final String RESOURCE_SOURCE = "class ProviderResource { String value = \"caf\u00e9\"; }";

    @Test
    void createsProviderFromClassLoaderResourceStream() throws IOException {
        ResourceClassLoader classLoader = new ResourceClassLoader(ProvidersTest.class.getClassLoader());

        try (Provider provider = Providers.resourceProvider(classLoader, RESOURCE_PATH, StandardCharsets.UTF_8)) {
            assertThat(readAllCharacters(provider)).isEqualTo(RESOURCE_SOURCE);
        }

        assertThat(classLoader.requestedResource()).isEqualTo(RESOURCE_PATH);
    }

    @Test
    void failsWhenClassLoaderResourceIsMissing() {
        ResourceClassLoader classLoader = new ResourceClassLoader(ProvidersTest.class.getClassLoader());
        String missingResource = "examples/missing-resource.java";

        assertThatIOException()
                .isThrownBy(() -> Providers.resourceProvider(classLoader, missingResource, StandardCharsets.UTF_8))
                .withMessage("Cannot find " + missingResource);
    }

    private static String readAllCharacters(Provider provider) throws IOException {
        StringBuilder source = new StringBuilder();
        char[] buffer = new char[8];
        int read;
        while ((read = provider.read(buffer, 0, buffer.length)) != -1) {
            source.append(buffer, 0, read);
        }
        return source.toString();
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private String requestedResource;

        private ResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            requestedResource = name;
            if (RESOURCE_PATH.equals(name)) {
                return new ByteArrayInputStream(RESOURCE_SOURCE.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        }

        private String requestedResource() {
            return requestedResource;
        }
    }
}
