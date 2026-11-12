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
import io.envoyproxy.envoy.config.core.v3.Http1ProtocolOptions;
import org.junit.jupiter.api.Test;

public class Http1ProtocolOptionsTest {
    @Test
    void roundTripsLegacyHttpHostAndTrailerSettings() throws Exception {
        Http1ProtocolOptions.Builder builder = Http1ProtocolOptions.newBuilder();
        JsonFormat.parser().merge("""
                {
                  "allowAbsoluteUrl": true,
                  "acceptHttp10": true,
                  "defaultHostForHttp10": "legacy.example",
                  "enableTrailers": true
                }
                """, builder);
        Http1ProtocolOptions options = builder.build();
        assertTrue(options.getAllowAbsoluteUrl().getValue());
        assertTrue(options.getAcceptHttp10());
        assertEquals("legacy.example", options.getDefaultHostForHttp10());
        assertTrue(options.getEnableTrailers());

        Http1ProtocolOptions.Builder copy = Http1ProtocolOptions.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().print(options), copy);
        assertEquals(options, copy.build());
    }
}
