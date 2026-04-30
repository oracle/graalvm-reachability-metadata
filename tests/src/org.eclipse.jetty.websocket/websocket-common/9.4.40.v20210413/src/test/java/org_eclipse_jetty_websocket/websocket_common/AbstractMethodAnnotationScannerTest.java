/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.websocket_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.common.events.JettyAnnotatedMetadata;
import org.eclipse.jetty.websocket.common.events.JettyAnnotatedScanner;
import org.junit.jupiter.api.Test;

public class AbstractMethodAnnotationScannerTest {

    @Test
    public void scansDeclaredMethodsForAnnotatedWebSocketCallbacks() {
        JettyAnnotatedScanner scanner = new JettyAnnotatedScanner();
        JettyAnnotatedMetadata metadata = scanner.scan(AnnotatedEndpoint.class);
        AnnotatedEndpoint endpoint = new AnnotatedEndpoint();

        assertThat(metadata.onText).isNotNull();
        assertThat(metadata.onClose).isNotNull();

        metadata.onText.call(endpoint, null, "hello");
        metadata.onClose.call(endpoint, null, 1000, "normal");

        assertThat(endpoint.events).containsExactly("text:hello", "close:1000:normal");
    }

    public static class BaseEndpoint {
        protected final List<String> events = new ArrayList<>();

        @OnWebSocketClose
        public void onClose(int statusCode, String reason) {
            events.add("close:" + statusCode + ":" + reason);
        }
    }

    public static class AnnotatedEndpoint extends BaseEndpoint {
        @OnWebSocketMessage
        public void onText(String message) {
            events.add("text:" + message);
        }
    }
}
