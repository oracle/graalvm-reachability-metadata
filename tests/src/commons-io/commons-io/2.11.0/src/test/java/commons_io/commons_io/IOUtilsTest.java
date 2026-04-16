/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_io.commons_io;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IOUtilsTest {
    @Test
    void resourceToURLUsesTheIOUtilsClassWhenNoClassLoaderIsProvided() {
        String resourceName = "/commons_io/commons_io/missing-resource.txt";

        assertThatThrownBy(() -> IOUtils.resourceToURL(resourceName))
                .isInstanceOf(IOException.class)
                .hasMessage("Resource not found: " + resourceName);
    }

    @Test
    void resourceToURLUsesTheProvidedClassLoaderWhenAvailable() {
        String resourceName = "commons_io/commons_io/missing-resource.txt";
        ClassLoader classLoader = getClass().getClassLoader();

        assertThatThrownBy(() -> IOUtils.resourceToURL(resourceName, classLoader))
                .isInstanceOf(IOException.class)
                .hasMessage("Resource not found: " + resourceName);
    }
}
