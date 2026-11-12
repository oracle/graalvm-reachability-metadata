/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.extensions.filters.http.router.v3.Router;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpFilter;
import org.junit.jupiter.api.Test;

public class HttpFilterTest {
    @Test
    void roundTripsTypedRouterFilter() throws Exception {
        JsonFormat.TypeRegistry registry = JsonFormat.TypeRegistry.newBuilder().add(Router.getDescriptor()).build();
        HttpFilter.Builder builder = HttpFilter.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge("""
                {
                  "name": "envoy.filters.http.router",
                  "typedConfig": {
                    "@type": "type.googleapis.com/envoy.extensions.filters.http.router.v3.Router",
                    "suppressEnvoyHeaders": true
                  }
                }
                """, builder);
        HttpFilter filter = builder.build();
        assertEquals("envoy.filters.http.router", filter.getName());
        assertTrue(filter.hasTypedConfig());
        assertTrue(filter.getTypedConfig().unpack(Router.class).getSuppressEnvoyHeaders());

        HttpFilter.Builder copy = HttpFilter.newBuilder();
        JsonFormat.parser().usingTypeRegistry(registry).merge(
                JsonFormat.printer().usingTypeRegistry(registry).print(filter), copy);
        assertEquals(filter, copy.build());
    }
}
