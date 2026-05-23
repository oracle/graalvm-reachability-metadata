/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.keycloak.common.constants.GenericConstants;
import org.keycloak.common.util.FindFile;

import static org.assertj.core.api.Assertions.assertThat;

public class FindFileTest {
    private static final String PRIMARY_RESOURCE =
            "META-INF/maven/org.keycloak/keycloak-client-common-synced/pom.properties";
    private static final String FALLBACK_RESOURCE =
            "org_keycloak/keycloak_client_common_synced/find-file-context-loader-only.txt";
    private static final String FALLBACK_CONTENT = "context-class-loader-resource";

    @Test
    void findsClasspathResourceWithLibraryClassLoader() throws IOException {
        String resourceLocation = GenericConstants.PROTOCOL_CLASSPATH + PRIMARY_RESOURCE;

        try (InputStream inputStream = FindFile.findFile(resourceLocation)) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("groupId=org.keycloak", "artifactId=keycloak-client-common-synced");
        }
    }

    @Test
    void fallsBackToContextClassLoaderForClasspathResource() throws IOException {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        ClassLoader contextClassLoader = new InMemoryResourceClassLoader(FALLBACK_RESOURCE, FALLBACK_CONTENT);

        try {
            thread.setContextClassLoader(contextClassLoader);
            String resourceLocation = GenericConstants.PROTOCOL_CLASSPATH + FALLBACK_RESOURCE;

            try (InputStream inputStream = FindFile.findFile(resourceLocation)) {
                assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
                        .isEqualTo(FALLBACK_CONTENT);
            }
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class InMemoryResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceBytes;

        private InMemoryResourceClassLoader(String resourceName, String resourceContent) {
            super(null);
            this.resourceName = resourceName;
            this.resourceBytes = resourceContent.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(resourceBytes);
            }
            return null;
        }
    }
}
