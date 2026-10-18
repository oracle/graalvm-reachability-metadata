/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pulsar.shade.org.apache.commons.codec.Resources;
import org.apache.pulsar.shade.org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class CommonsCodecResourcesTest {

    @Test
    void opensARequiredClasspathResource() throws Exception {
        try (InputStream input = Resources.getInputStream("/META-INF/io.netty.versions.properties")) {
            assertThat(IOUtils.toString(input, StandardCharsets.UTF_8)).contains("artifactId=pulsar-test-transport");
        }
    }
}
