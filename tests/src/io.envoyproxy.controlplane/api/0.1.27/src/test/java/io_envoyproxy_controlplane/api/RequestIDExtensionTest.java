/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.RequestIDExtension;
import org.junit.jupiter.api.Test;

public class RequestIDExtensionTest {
    @Test
    void roundTripsCustomRequestIdExtensionConfiguration() throws Exception {
        JsonFormat.TypeRegistry registry = JsonFormat.TypeRegistry.newBuilder().add(Struct.getDescriptor()).build();
        RequestIDExtension.Builder builder = RequestIDExtension.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge("""
                {"typedConfig": {"@type": "type.googleapis.com/google.protobuf.Struct", "value": {"prefix": "tenant"}}}
                """, builder);
        RequestIDExtension extension = builder.build();
        Struct config = extension.getTypedConfig().unpack(Struct.class);
        assertEquals("tenant", config.getFieldsOrThrow("prefix").getStringValue());

        RequestIDExtension.Builder copy = RequestIDExtension.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge(
                JsonFormat.printer().usingTypeRegistry(registry).print(extension), copy);
        assertEquals(extension, copy.build());
    }
}
