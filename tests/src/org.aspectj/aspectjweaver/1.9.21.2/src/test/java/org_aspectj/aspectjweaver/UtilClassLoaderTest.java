/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.aspectj.util.UtilClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilClassLoaderTest {
    private static final String VERSION_RESOURCE = "org/aspectj/bridge/version.properties";

    @Test
    void delegatesResourceLookupToSystemClassLoader() throws Exception {
        UtilClassLoader classLoader = new UtilClassLoader(new URL[0], new File[0]);

        URL resource = classLoader.getResource(VERSION_RESOURCE);
        InputStream resourceStream = classLoader.getResourceAsStream(VERSION_RESOURCE);

        assertThat(resource).isNotNull();
        assertThat(resource.toString()).endsWith(VERSION_RESOURCE);
        assertThat(resourceStream).isNotNull();
        try (InputStream stream = resourceStream) {
            String properties = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertThat(properties).contains("version.text");
        }
    }
}
