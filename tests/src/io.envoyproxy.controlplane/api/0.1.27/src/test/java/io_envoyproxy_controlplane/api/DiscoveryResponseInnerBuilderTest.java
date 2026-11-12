/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.config.core.v3.ControlPlane;
import io.envoyproxy.envoy.config.route.v3.RouteConfiguration;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import org.junit.jupiter.api.Test;

public class DiscoveryResponseInnerBuilderTest {
    @Test
    void editsNestedResourcesAndResetsEnvelopeWithoutChangingSnapshot() throws Exception {
        Any initial = Any.pack(RouteConfiguration.newBuilder().setName("initial").build());
        Any updated = Any.pack(RouteConfiguration.newBuilder().setName("updated").build());
        DiscoveryResponse.Builder builder = DiscoveryResponse.newBuilder()
                .setVersionInfo("revision-a")
                .setTypeUrl(initial.getTypeUrl())
                .setNonce("token-a")
                .setCanary(true)
                .setControlPlane(ControlPlane.newBuilder().setIdentifier("management-a").build())
                .addResources(initial);
        DiscoveryResponse snapshot = builder.build();
        builder.setResources(0, updated);
        builder.getResourcesBuilder(0).mergeFrom(updated);
        builder.getControlPlaneBuilder().setIdentifier("management-b");
        builder.setVersionInfoBytes(ByteString.copyFromUtf8("revision-b"));
        builder.setNonceBytes(ByteString.copyFromUtf8("token-b"));
        builder.setTypeUrlBytes(ByteString.copyFromUtf8(updated.getTypeUrl()));
        assertEquals("revision-b", builder.getVersionInfo());
        assertEquals("token-b", builder.getNonce());
        assertEquals(updated.getTypeUrl(), builder.getTypeUrl());
        assertTrue(builder.getCanary());
        assertTrue(builder.hasControlPlane());
        assertEquals("management-b", builder.getControlPlane().getIdentifier());
        assertEquals(1, builder.getResourcesCount());
        assertEquals(updated, builder.getResources(0));
        assertEquals(updated, builder.getResourcesList().get(0));

        JsonFormat.TypeRegistry registry = JsonFormat.TypeRegistry.newBuilder()
                .add(RouteConfiguration.getDescriptor()).build();
        DiscoveryResponse.Builder copy = DiscoveryResponse.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge(
                JsonFormat.printer().usingTypeRegistry(registry).print(builder), copy);
        assertEquals(builder.build(), copy.build());
        builder.clearCanary().clearControlPlane().clearNonce().clearResources().clearTypeUrl().clearVersionInfo();
        assertFalse(builder.hasControlPlane());
        assertEquals("{}", JsonFormat.printer().omittingInsignificantWhitespace().print(builder));
        assertEquals(DiscoveryResponse.getDefaultInstance(), builder.build());
        assertEquals("revision-a", snapshot.getVersionInfo());
        assertEquals("initial", snapshot.getResources(0).unpack(RouteConfiguration.class).getName());
        assertEquals("management-a", snapshot.getControlPlane().getIdentifier());
    }
}
