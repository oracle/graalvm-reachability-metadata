/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_core_client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpoint;
import org.junit.jupiter.api.Test;

public class JGroupsFileBroadcastEndpointTest {
    @Test
    void readsJGroupsConfigurationFromContextClassLoaderResource() throws Exception {
        String resourceName = "org_apache_activemq/artemis_core_client/jgroups-file-broadcast-endpoint.xml";
        JGroupsFileBroadcastEndpoint endpoint = new JGroupsFileBroadcastEndpoint(
                null,
                resourceName,
                "metadata-test-channel");

        Throwable failure = catchThrowable(endpoint::createChannel);

        assertThat(failure).isNotNull();
        assertThat(failure).isNotInstanceOf(NoClassDefFoundError.class);
        assertThat(String.valueOf(failure.getMessage()))
                .doesNotContain("couldn't find JGroups configuration");
    }
}
