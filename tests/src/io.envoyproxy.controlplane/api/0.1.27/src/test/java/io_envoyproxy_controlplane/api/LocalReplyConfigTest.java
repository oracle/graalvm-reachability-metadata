/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_envoyproxy_controlplane.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.util.JsonFormat;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.LocalReplyConfig;
import org.junit.jupiter.api.Test;

public class LocalReplyConfigTest {
    @Test
    void roundTripsLocalErrorResponseFormat() throws Exception {
        LocalReplyConfig.Builder builder = LocalReplyConfig.newBuilder();
        JsonFormat.parser().merge("""
                {"bodyFormat": {"textFormat": "%RESPONSE_CODE%: %LOCAL_REPLY_BODY%", "contentType": "text/plain"}}
                """, builder);
        LocalReplyConfig config = builder.build();
        assertEquals("%RESPONSE_CODE%: %LOCAL_REPLY_BODY%", config.getBodyFormat().getTextFormat());
        assertEquals("text/plain", config.getBodyFormat().getContentType());

        LocalReplyConfig.Builder copy = LocalReplyConfig.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().print(config), copy);
        assertEquals(config, copy.build());
    }
}
