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

public class IOUtilsTest {
    @Test
    void resolvesResourcesWithClassAndClassLoaderLookups() throws Exception {
        final URL byClass = IOUtils.resourceToURL("IOUtils.class");
        final URL byLoader = IOUtils.resourceToURL(
                "org/apache/pulsar/shade/org/apache/commons/io/IOUtils.class", IOUtils.class.getClassLoader());

        assertThat(byClass).isNotNull();
        assertThat(byLoader).isNotNull();
    }
}
