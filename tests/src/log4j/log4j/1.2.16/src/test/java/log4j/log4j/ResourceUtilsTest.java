/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.log4j.lf5.util.Resource;
import org.apache.log4j.lf5.util.ResourceUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceUtilsTest {

    private static final String TEST_CLASS_RESOURCE = ResourceUtilsTest.class.getName().replace('.', '/') + ".class";

    @Test
    void loadsResourceStreamFromObjectClassLoader() throws IOException {
        Resource resource = new Resource(TEST_CLASS_RESOURCE);

        try (InputStream inputStream = ResourceUtils.getResourceAsStream(this, resource)) {
            assertThat(inputStream).isNotNull();
            assertThat(inputStream.read()).isNotEqualTo(-1);
        }
    }

    @Test
    void loadsResourceUrlFromObjectClassLoader() {
        Resource resource = new Resource(TEST_CLASS_RESOURCE);
        URL resourceUrl = ResourceUtils.getResourceAsURL(this, resource);

        assertThat(resourceUrl).isNotNull();
        assertThat(resourceUrl.toExternalForm()).contains(TEST_CLASS_RESOURCE);
    }

    @Test
    void loadsResourceStreamFromSystemClassLoaderWhenObjectUsesBootstrapLoader() throws IOException {
        Resource resource = new Resource(TEST_CLASS_RESOURCE);

        try (InputStream inputStream = ResourceUtils.getResourceAsStream("bootstrap", resource)) {
            assertThat(inputStream).isNotNull();
            assertThat(inputStream.read()).isNotEqualTo(-1);
        }
    }

    @Test
    void loadsResourceUrlFromSystemClassLoaderWhenObjectUsesBootstrapLoader() {
        Resource resource = new Resource(TEST_CLASS_RESOURCE);
        URL resourceUrl = ResourceUtils.getResourceAsURL("bootstrap", resource);

        assertThat(resourceUrl).isNotNull();
        assertThat(resourceUrl.toExternalForm()).contains(TEST_CLASS_RESOURCE);
    }
}
