/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.WebConnection;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestTest {

    @Test
    void createsInternalUpgradeHandlerAndSwitchesProtocols() throws Exception {
        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        org.apache.coyote.Response coyoteResponse = new org.apache.coyote.Response();
        Request request = new Request(null, coyoteRequest);
        Response response = new Response(coyoteResponse);
        StandardContext context = new StandardContext();
        context.setName("upgrade-test");
        context.setPath("/upgrade-test");
        request.getMappingData().context = context;
        request.setResponse(response);
        response.setRequest(request);

        TestUpgradeHandler handler = request.upgrade(TestUpgradeHandler.class);

        assertThat(handler).isNotNull();
        assertThat(coyoteResponse.getStatus()).isEqualTo(HttpServletResponse.SC_SWITCHING_PROTOCOLS);
    }

    public static final class TestUpgradeHandler implements InternalHttpUpgradeHandler {
        public TestUpgradeHandler() {
        }

        @Override
        public SocketState upgradeDispatch(SocketEvent status) {
            return SocketState.UPGRADED;
        }

        @Override
        public void timeoutAsync(long now) {
        }

        @Override
        public void setSocketWrapper(SocketWrapperBase<?> wrapper) {
        }

        @Override
        public void setSslSupport(SSLSupport sslSupport) {
        }

        @Override
        public void pause() {
        }

        @Override
        public void init(WebConnection connection) {
        }

        @Override
        public void destroy() {
        }
    }
}
