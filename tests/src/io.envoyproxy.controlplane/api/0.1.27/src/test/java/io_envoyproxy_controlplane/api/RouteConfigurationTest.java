/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.config.route.v3.RouteConfiguration;
import org.junit.jupiter.api.Test;

public class RouteConfigurationTest {
    @Test
    void roundTripsVirtualHostRoutingTable() throws Exception {
        RouteConfiguration.Builder builder = RouteConfiguration.newBuilder();
        JsonFormat.parser().merge("""
                {
                  "name": "application-routes",
                  "virtualHosts": [{
                    "name": "application",
                    "domains": ["api.example"],
                    "routes": [{"match": {"prefix": "/api/"}, "route": {"cluster": "backend"}}]
                  }],
                  "responseHeadersToRemove": ["x-internal"]
                }
                """, builder);
        RouteConfiguration routes = builder.build();
        assertEquals("application-routes", routes.getName());
        assertEquals(List.of("api.example"), routes.getVirtualHosts(0).getDomainsList());
        assertEquals("/api/", routes.getVirtualHosts(0).getRoutes(0).getMatch().getPrefix());
        assertEquals("backend", routes.getVirtualHosts(0).getRoutes(0).getRoute().getCluster());
        assertEquals(List.of("x-internal"), routes.getResponseHeadersToRemoveList());

        RouteConfiguration.Builder copy = RouteConfiguration.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().print(routes), copy);
        assertEquals(routes, copy.build());
    }
}
