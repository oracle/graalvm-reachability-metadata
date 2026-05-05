/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import org.junit.jupiter.api.Test;
import org.keycloak.common.constants.GenericConstants;
import org.keycloak.common.util.FindFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class FindFileTest {
    @Test
    void findsClasspathResourceFromContextClassLoaderWhenDefaultClassLoaderMisses() throws IOException {
        String resourceName = "context-only-keycloak-config.txt";
        String resourceContent = "realm=context-loader";
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(new ContextResourceClassLoader(resourceName, resourceContent));

            try (InputStream resource = FindFile.findFile(GenericConstants.PROTOCOL_CLASSPATH + resourceName)) {
                String content = new String(resource.readAllBytes(), StandardCharsets.UTF_8);

                assertThat(content).isEqualTo(resourceContent);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class ContextResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final byte[] resourceContent;

        private ContextResourceClassLoader(String resourceName, String resourceContent) {
            super(null);
            this.resourceName = resourceName;
            this.resourceContent = resourceContent.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (resourceName.equals(name)) {
                return new ByteArrayInputStream(resourceContent);
            }
            return null;
        }
    }
}
