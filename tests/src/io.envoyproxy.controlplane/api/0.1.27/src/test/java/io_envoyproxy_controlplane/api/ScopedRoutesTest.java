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
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.ScopedRoutes;
import org.junit.jupiter.api.Test;

public class ScopedRoutesTest {
    @Test
    void roundTripsHeaderBasedScopedRoutingSubscription() throws Exception {
        ScopedRoutes.Builder builder = ScopedRoutes.newBuilder();
        JsonFormat.parser().merge("""
                {
                  "name": "tenant-routes",
                  "scopeKeyBuilder": {"fragments": [{"headerValueExtractor": {"name": "x-tenant", "index": 0}}]},
                  "rdsConfigSource": {"ads": {}, "resourceApiVersion": "V3"},
                  "scopedRds": {"scopedRdsConfigSource": {"ads": {}, "resourceApiVersion": "V3"}}
                }
                """, builder);
        ScopedRoutes routes = builder.build();
        assertEquals("tenant-routes", routes.getName());
        assertEquals("x-tenant", routes.getScopeKeyBuilder().getFragments(0).getHeaderValueExtractor().getName());
        assertTrue(routes.getRdsConfigSource().hasAds());
        assertTrue(routes.hasScopedRds());
        assertTrue(routes.getScopedRds().getScopedRdsConfigSource().hasAds());

        ScopedRoutes.Builder copy = ScopedRoutes.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().print(routes), copy);
        assertEquals(routes, copy.build());
    }
}
