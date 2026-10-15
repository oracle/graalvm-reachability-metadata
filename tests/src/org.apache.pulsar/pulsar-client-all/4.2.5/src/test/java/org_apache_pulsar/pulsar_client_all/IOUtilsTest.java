/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.apache.pulsar.shade.org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class IOUtilsTest {
    @Test
    void resolvesResourcesWithClassAndExplicitClassLoader() throws Exception {
        URL relative = IOUtils.resourceToURL("IOUtils.class");
        URL explicit = IOUtils.resourceToURL(
                "org/apache/pulsar/shade/org/apache/commons/io/IOUtils.class",
                IOUtils.class.getClassLoader());

        assertThat(relative.toExternalForm()).endsWith("IOUtils.class");
        assertThat(explicit.toExternalForm()).endsWith("IOUtils.class");
    }
}
