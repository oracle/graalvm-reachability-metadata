/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_core_client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationDownstreamConfiguration;
import org.apache.activemq.artemis.core.config.federation.FederationPolicy;
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.FederationDownstreamConnectMessage;
import org.junit.jupiter.api.Test;

public class FederationStreamConnectMessageTest {
    @Test
    void decodesFederationPoliciesFromEncodedClassNames() {
        FederationDownstreamConnectMessage source = new FederationDownstreamConnectMessage();
        source.setName("downstream-connect");
        source.setFederationPolicyMap(federationPolicies());
        source.setStreamConfiguration(downstreamConfiguration());

        ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(512);
        source.encodeRest(buffer);

        FederationDownstreamConnectMessage decoded = new FederationDownstreamConnectMessage();
        decoded.decodeRest(buffer);

        assertThat(decoded.getName()).isEqualTo("downstream-connect");
        assertThat(decoded.getStreamConfiguration().getName()).isEqualTo("downstream");
        assertThat(decoded.getStreamConfiguration().getPolicyRefs()).containsExactly("orders");
        assertThat(decoded.getStreamConfiguration().getUpstreamConfiguration())
                .isEqualTo(source.getStreamConfiguration().getUpstreamConfiguration());
        assertThat(decoded.getFederationPolicyMap())
                .containsOnlyKeys("orders")
                .containsEntry("orders", queuePolicy());
    }

    private static Map<String, FederationPolicy> federationPolicies() {
        Map<String, FederationPolicy> policies = new HashMap<>();
        FederationQueuePolicyConfiguration queuePolicy = queuePolicy();
        policies.put(queuePolicy.getName(), queuePolicy);
        return policies;
    }

    private static FederationQueuePolicyConfiguration queuePolicy() {
        return new FederationQueuePolicyConfiguration()
                .setName("orders")
                .setIncludeFederated(true)
                .setPriorityAdjustment(3)
                .addInclude(new FederationQueuePolicyConfiguration.Matcher()
                        .setAddressMatch("orders.#")
                        .setQueueMatch("orders.queue"));
    }

    private static FederationDownstreamConfiguration downstreamConfiguration() {
        FederationDownstreamConfiguration configuration = new FederationDownstreamConfiguration()
                .setName("downstream")
                .addPolicyRef("orders");
        configuration.setUpstreamConfiguration(upstreamTransportConfiguration());
        return configuration;
    }

    private static TransportConfiguration upstreamTransportConfiguration() {
        Map<String, Object> params = new HashMap<>();
        params.put("host", "127.0.0.1");
        params.put("port", 61616);
        return new TransportConfiguration("example.UpstreamConnectorFactory", params, "upstream-connector");
    }
}
