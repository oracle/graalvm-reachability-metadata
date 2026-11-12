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
import io.envoyproxy.envoy.config.core.v3.Http2ProtocolOptions;
import org.junit.jupiter.api.Test;

public class Http2ProtocolOptionsTest {
    @Test
    void roundTripsFlowControlAndConnectSettings() throws Exception {
        Http2ProtocolOptions.Builder builder = Http2ProtocolOptions.newBuilder();
        JsonFormat.parser().merge("""
                {
                  "hpackTableSize": 8192,
                  "maxConcurrentStreams": 100,
                  "initialStreamWindowSize": 131072,
                  "initialConnectionWindowSize": 1048576,
                  "allowConnect": true
                }
                """, builder);
        Http2ProtocolOptions options = builder.build();
        assertEquals(8192, options.getHpackTableSize().getValue());
        assertEquals(100, options.getMaxConcurrentStreams().getValue());
        assertEquals(131072, options.getInitialStreamWindowSize().getValue());
        assertEquals(1048576, options.getInitialConnectionWindowSize().getValue());
        assertTrue(options.getAllowConnect());

        Http2ProtocolOptions.Builder copy = Http2ProtocolOptions.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().print(options), copy);
        assertEquals(options, copy.build());
    }
}
