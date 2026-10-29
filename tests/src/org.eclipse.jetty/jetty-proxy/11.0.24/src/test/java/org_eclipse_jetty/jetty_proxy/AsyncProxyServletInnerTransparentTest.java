/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_proxy;

import java.util.Map;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringRequestContent;
import org.eclipse.jetty.proxy.AsyncProxyServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class AsyncProxyServletInnerTransparentTest extends ProxyTestSupport {
    @Test
    void rewritesAndStreamsPostThroughReverseProxy() throws Exception {
        startProxy(new AsyncProxyServlet.Transparent(),
                Map.of("proxyTo", upstreamUrl("/service"), "prefix", "/public"), false);
        String payload = "asynchronous reverse proxy content\n".repeat(1024);

        ContentResponse response = reverseRequest("/public/upload?mode=stream").method("POST")
                .body(new StringRequestContent(payload)).send();

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeaders().get("X-Upstream-Method")).isEqualTo("POST");
        assertThat(response.getHeaders().get("X-Upstream-Path")).isEqualTo("/service/upload");
        assertThat(response.getHeaders().get("X-Upstream-Query")).isEqualTo("mode=stream");
        assertThat(response.getContentAsString()).isEqualTo(payload);
    }
}
