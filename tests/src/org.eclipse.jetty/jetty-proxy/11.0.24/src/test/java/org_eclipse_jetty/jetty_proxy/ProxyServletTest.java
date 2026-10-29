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
import org.eclipse.jetty.proxy.ProxyServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class ProxyServletTest extends ProxyTestSupport {
    @Test
    void forwardsRequestsAndFiltersHopByHopHeaders() throws Exception {
        startProxy(new ProxyServlet(), Map.of("whiteList", upstreamAddress()), true);

        ContentResponse response = forwardRequest("/message?name=jetty&mode=proxy")
                .header("X-Token", "request-token")
                .header("Connection", "X-Hop")
                .header("X-Hop", "not-for-upstream")
                .send();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("upstream:/message");
        assertThat(response.getHeaders().get("X-Upstream-Method")).isEqualTo("GET");
        assertThat(response.getHeaders().get("X-Upstream-Query")).isEqualTo("name=jetty&mode=proxy");
        assertThat(response.getHeaders().get("X-Upstream-Token")).isEqualTo("request-token");
        assertThat(response.getHeaders().get("X-Upstream-Hop")).isNull();
        assertThat(response.getHeaders().get("X-Upstream-Via")).contains("proxy-test");
        assertThat(response.getHeaders().get("X-Upstream-Forwarded-For")).isEqualTo("127.0.0.1");
        assertThat(upstreamRequests.get()).isEqualTo(1);
    }

    @Test
    void forwardsPostContentAndCreatedStatus() throws Exception {
        startProxy(new ProxyServlet(), Map.of(), true);
        String payload = "payload forwarded through the blocking input stream";

        ContentResponse response = forwardRequest("/upload").method("POST")
                .body(new StringRequestContent(payload)).send();

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeaders().get("X-Upstream-Method")).isEqualTo("POST");
        assertThat(response.getContentAsString()).isEqualTo(payload);
    }

    @Test
    void forwardsContentAfterContinueResponse() throws Exception {
        startProxy(new ProxyServlet(), Map.of(), true);
        String payload = "content sent after the upstream accepts the request";

        ContentResponse response = forwardRequest("/upload").method("POST")
                .header("Expect", "100-continue").body(new StringRequestContent(payload)).send();

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo(payload);
        assertThat(upstreamRequests.get()).isEqualTo(1);
    }

    @Test
    void rejectsBlacklistedDestinationsWithoutContactingUpstream() throws Exception {
        startProxy(new ProxyServlet(), Map.of("blackList", upstreamAddress()), true);

        ContentResponse response = forwardRequest("/private").send();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(upstreamRequests.get()).isZero();
    }
}
