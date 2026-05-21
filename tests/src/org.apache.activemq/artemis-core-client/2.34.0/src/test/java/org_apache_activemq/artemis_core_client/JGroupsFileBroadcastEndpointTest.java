/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_core_client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpoint;
import org.junit.jupiter.api.Test;

public class JGroupsFileBroadcastEndpointTest {
    @Test
    void reportsMissingJGroupsConfigurationFromContextClassLoaderResource() throws Exception {
        String resourceName = "missing-jgroups-test.xml";
        JGroupsFileBroadcastEndpoint endpoint = new JGroupsFileBroadcastEndpoint(
                null,
                resourceName,
                "metadata-test-channel");

        assertThatThrownBy(endpoint::createChannel)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("couldn't find JGroups configuration " + resourceName);
    }
}
