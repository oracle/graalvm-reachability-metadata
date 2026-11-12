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

import java.util.List;

import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.extensions.filters.http.router.v3.Router;
import org.junit.jupiter.api.Test;

public class RouterTest {
    @Test
    void roundTripsRouterAndPrintsMutableBuilder() throws Exception {
        Router.Builder builder = Router.newBuilder();
        JsonFormat.parser().merge("""
                {
                  "dynamicStats": false,
                  "startChildSpan": true,
                  "suppressEnvoyHeaders": true,
                  "strictCheckHeaders": ["x-envoy-upstream-rq-timeout-ms"],
                  "respectExpectedRqTimeout": true
                }
                """, builder);
        Router router = builder.build();
        assertTrue(router.hasDynamicStats());
        assertFalse(router.getDynamicStats().getValue());
        assertTrue(router.getStartChildSpan());
        assertTrue(router.getSuppressEnvoyHeaders());
        assertTrue(router.getRespectExpectedRqTimeout());
        assertEquals(List.of("x-envoy-upstream-rq-timeout-ms"), router.getStrictCheckHeadersList());

        Router.Builder copy = Router.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().print(builder), copy);
        assertEquals(router, copy.build());
        copy.clearStrictCheckHeaders();
        assertTrue(copy.build().getStrictCheckHeadersList().isEmpty());
        assertEquals(1, router.getStrictCheckHeadersCount());
    }
}
