/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.config.core.v3.HttpProtocolOptions;
import io.envoyproxy.envoy.config.core.v3.HttpProtocolOptions.HeadersWithUnderscoresAction;
import org.junit.jupiter.api.Test;

public class HttpProtocolOptionsTest {
    @Test
    void roundTripsConnectionLimitsAndHeaderPolicy() throws Exception {
        HttpProtocolOptions.Builder builder = HttpProtocolOptions.newBuilder();
        JsonFormat.parser().merge("""
                {
                  "idleTimeout": "60s",
                  "maxConnectionDuration": "3600s",
                  "maxHeadersCount": 80,
                  "maxStreamDuration": "300s",
                  "headersWithUnderscoresAction": "REJECT_REQUEST"
                }
                """, builder);
        HttpProtocolOptions options = builder.build();
        assertEquals(60, options.getIdleTimeout().getSeconds());
        assertEquals(3600, options.getMaxConnectionDuration().getSeconds());
        assertEquals(80, options.getMaxHeadersCount().getValue());
        assertEquals(300, options.getMaxStreamDuration().getSeconds());
        assertEquals(HeadersWithUnderscoresAction.REJECT_REQUEST, options.getHeadersWithUnderscoresAction());

        HttpProtocolOptions.Builder copy = HttpProtocolOptions.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().print(options), copy);
        assertEquals(options, copy.build());
    }
}
