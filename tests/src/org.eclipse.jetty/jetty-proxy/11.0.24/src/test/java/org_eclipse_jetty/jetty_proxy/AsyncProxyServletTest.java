/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_proxy;

import java.util.Map;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BytesRequestContent;
import org.eclipse.jetty.proxy.AsyncProxyServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class AsyncProxyServletTest extends ProxyTestSupport {
    @Test
    void streamsBinaryRequestAndResponseAcrossMultipleBuffers() throws Exception {
        startProxy(new AsyncProxyServlet(), Map.of("requestBufferSize", "1024", "responseBufferSize", "1024"), true);
        byte[] payload = new byte[128 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31);
        }

        ContentResponse response = forwardRequest("/binary?transfer=async").method("POST")
                .body(new BytesRequestContent("application/octet-stream", payload)).send();

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeaders().get("X-Upstream-Query")).isEqualTo("transfer=async");
        assertThat(response.getHeaders().get("Content-Type")).isEqualTo("application/octet-stream");
        assertThat(response.getContent()).isEqualTo(payload);
        assertThat(upstreamRequests.get()).isEqualTo(1);
    }
}
