/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import org.apache.pulsar.shade.com.google.common.io.Resources;
import org.junit.jupiter.api.Test;

public class ResourcesTest {

    @Test
    void locatesBundledResourcesByLoaderAndContextClass() {
        final URL clientDefaults = Resources.getResource(
                "org/apache/pulsar/shade/org/asynchttpclient/config/ahc-default.properties");
        final URL classFile = Resources.getResource(ResourcesTest.class, "ResourcesTest.class");

        assertThat(clientDefaults).isNotNull();
        assertThat(classFile).isNotNull();
    }
}
