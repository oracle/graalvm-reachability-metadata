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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ResourcesTest {
    @Test
    void findsResourcesByClassLoaderAndRelativeClassName() {
        URL service = Resources.getResource(
                "META-INF/services/org.apache.pulsar.shade.javax.ws.rs.client.ClientBuilder");
        URL ownClass = Resources.getResource(Resources.class, "Resources.class");

        assertThat(service.toExternalForm()).contains("META-INF/services");
        assertThat(ownClass.toExternalForm()).endsWith("Resources.class");
    }
}
