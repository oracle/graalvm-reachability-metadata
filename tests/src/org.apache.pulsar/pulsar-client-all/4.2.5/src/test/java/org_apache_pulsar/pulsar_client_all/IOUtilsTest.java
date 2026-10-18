/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.pulsar.shade.org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class IOUtilsTest {

    private static final String RESOURCE = "/META-INF/io.netty.versions.properties";

    @Test
    void locatesResourcesWithAClassAndAnExplicitClassLoader() throws Exception {
        final URL classResource = IOUtils.resourceToURL(RESOURCE);
        final URL loaderResource = IOUtils.resourceToURL(RESOURCE.substring(1), getClass().getClassLoader());

        assertThat(classResource).isEqualTo(loaderResource);
        try (InputStream input = classResource.openStream()) {
            assertThat(IOUtils.toString(input, StandardCharsets.UTF_8))
                    .contains("pulsar-test-transport.version=fixture");
        }
    }
}
