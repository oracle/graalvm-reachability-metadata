/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.config.route.v3.RouteConfiguration;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import org.junit.jupiter.api.Test;

public class DiscoveryResponseTest {
    @Test
    void roundTripsDiscoveryEnvelopeWithTypedResources() throws Exception {
        JsonFormat.TypeRegistry registry = JsonFormat.TypeRegistry.newBuilder()
                .add(RouteConfiguration.getDescriptor()).build();
        DiscoveryResponse.Builder builder = DiscoveryResponse.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge("""
                {
                  "versionInfo": "revision-a",
                  "resources": [{"@type": "type.googleapis.com/envoy.config.route.v3.RouteConfiguration", "name": "routes"}],
                  "canary": true,
                  "typeUrl": "type.googleapis.com/envoy.config.route.v3.RouteConfiguration",
                  "nonce": "response-token",
                  "controlPlane": {"identifier": "management-east"}
                }
                """, builder);
        DiscoveryResponse response = builder.build();
        assertEquals("revision-a", response.getVersionInfo());
        assertEquals(ByteString.copyFromUtf8("revision-a"), response.getVersionInfoBytes());
        assertEquals("response-token", response.getNonce());
        assertEquals(ByteString.copyFromUtf8("response-token"), response.getNonceBytes());
        assertEquals("type.googleapis.com/envoy.config.route.v3.RouteConfiguration", response.getTypeUrl());
        assertEquals(ByteString.copyFromUtf8(response.getTypeUrl()), response.getTypeUrlBytes());
        assertTrue(response.getCanary());
        assertTrue(response.hasControlPlane());
        assertEquals("management-east", response.getControlPlane().getIdentifier());
        assertEquals(1, response.getResourcesCount());
        assertEquals(response.getResources(0), response.getResourcesList().get(0));
        assertEquals("routes", response.getResources(0).unpack(RouteConfiguration.class).getName());

        DiscoveryResponse.Builder copy = DiscoveryResponse.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge(
                JsonFormat.printer().usingTypeRegistry(registry).print(response), copy);
        assertEquals(response, copy.build());
        assertEquals(response, DiscoveryResponse.parseFrom(response.toByteArray()));
    }
}
