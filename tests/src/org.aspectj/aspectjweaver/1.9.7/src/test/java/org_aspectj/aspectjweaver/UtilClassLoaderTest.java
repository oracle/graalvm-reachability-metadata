/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.aspectj.util.UtilClassLoader;
import org.junit.jupiter.api.Test;

public class UtilClassLoaderTest {
    private static final String RESOURCE_NAME = "org_aspectj/aspectjweaver/util-class-loader-resource.txt";
    private static final String RESOURCE_CONTENT = "loaded through UtilClassLoader";

    @Test
    void delegatesResourceLookupToSystemClassLoader() throws Exception {
        UtilClassLoader loader = new UtilClassLoader(new URL[0], new File[0]);

        URL resource = loader.getResource(RESOURCE_NAME);

        assertThat(resource).isNotNull();
    }

    @Test
    void delegatesResourceStreamLookupToSystemClassLoader() throws Exception {
        UtilClassLoader loader = new UtilClassLoader(new URL[0], new File[0]);

        try (InputStream resourceStream = loader.getResourceAsStream(RESOURCE_NAME)) {
            assertThat(resourceStream).isNotNull();
            String resourceContent = new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(resourceContent).isEqualTo(RESOURCE_CONTENT);
        }
    }
}
