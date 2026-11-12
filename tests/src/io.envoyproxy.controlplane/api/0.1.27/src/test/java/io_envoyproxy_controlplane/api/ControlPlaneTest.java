/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.config.core.v3.ControlPlane;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import org.junit.jupiter.api.Test;

public class ControlPlaneTest {
    @Test
    void parsesControlPlaneIdentityInsideDiscoveryEnvelope() throws Exception {
        DiscoveryResponse.Builder envelope = DiscoveryResponse.newBuilder();
        JsonFormat.parser().merge("""
                {"controlPlane": {"identifier": "management-west"}}
                """, envelope);
        assertEquals("management-west", envelope.getControlPlane().getIdentifier());

        ControlPlane.Builder copy = ControlPlane.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().print(envelope.getControlPlane()), copy);
        assertEquals(envelope.getControlPlane(), copy.build());
    }
}
