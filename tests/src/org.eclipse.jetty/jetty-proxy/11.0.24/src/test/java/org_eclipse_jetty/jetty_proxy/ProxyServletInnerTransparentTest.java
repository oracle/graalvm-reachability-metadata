/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_proxy;

import java.util.Map;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class ProxyServletInnerTransparentTest extends ProxyTestSupport {
    @Test
    void rewritesPrefixAndPreservesQuery() throws Exception {
        startProxy(new ProxyServlet.Transparent(),
                Map.of("proxyTo", upstreamUrl("/backend/"), "prefix", "/gateway"), false);

        ContentResponse response = reverseRequest("/gateway/items?category=books&sort=title").send();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("upstream:/backend/items");
        assertThat(response.getHeaders().get("X-Upstream-Path")).isEqualTo("/backend/items");
        assertThat(response.getHeaders().get("X-Upstream-Query")).isEqualTo("category=books&sort=title");
    }

    @Test
    void passesRedirectBackToClientWithoutFollowingIt() throws Exception {
        startProxy(new ProxyServlet.Transparent(),
                Map.of("proxyTo", upstreamUrl("/backend"), "prefix", "/gateway"), false);

        ContentResponse response = reverseRequest("/gateway/redirect").send();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeaders().get("Location")).isEqualTo("/landing");
        assertThat(upstreamRequests.get()).isEqualTo(1);
    }

    @Test
    void rejectsRequestsOutsideConfiguredPrefix() throws Exception {
        startProxy(new ProxyServlet.Transparent(),
                Map.of("proxyTo", upstreamUrl("/backend"), "prefix", "/gateway"), false);

        ContentResponse response = reverseRequest("/outside/items").send();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(upstreamRequests.get()).isZero();
    }
}
