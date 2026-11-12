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
import io.envoyproxy.envoy.config.core.v3.ApiVersion;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.Rds;
import org.junit.jupiter.api.Test;

public class RdsTest {
    @Test
    void roundTripsAggregatedRouteSubscription() throws Exception {
        Rds.Builder builder = Rds.newBuilder();
        JsonFormat.parser().merge("""
                {"routeConfigName": "application-routes", "configSource": {"ads": {}, "resourceApiVersion": "V3"}}
                """, builder);
        Rds subscription = builder.build();
        assertEquals("application-routes", subscription.getRouteConfigName());
        assertTrue(subscription.getConfigSource().hasAds());
        assertEquals(ApiVersion.V3, subscription.getConfigSource().getResourceApiVersion());

        Rds.Builder copy = Rds.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().print(subscription), copy);
        assertEquals(subscription, copy.build());
    }
}
