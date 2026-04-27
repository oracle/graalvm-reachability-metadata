/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_io.commons_io;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class IOUtilsTest {

    private static final String RESOURCE_PATH = "commons-io/test-resource.txt";

    @Test
    void resolvesResourcesUsingIoUtilsClassWhenNoClassLoaderIsProvided() throws Exception {
        URL resource = IOUtils.resourceToURL("/" + RESOURCE_PATH);

        assertThat(resource).isNotNull();
        assertThat(IOUtils.toString(resource, StandardCharsets.UTF_8)).isEqualTo("commons-io test resource\n");
    }

    @Test
    void resolvesResourcesUsingTheProvidedClassLoader() throws Exception {
        URL resource = IOUtils.resourceToURL(RESOURCE_PATH, getClass().getClassLoader());

        assertThat(resource).isNotNull();
        assertThat(IOUtils.toString(resource, StandardCharsets.UTF_8)).isEqualTo("commons-io test resource\n");
    }
}
