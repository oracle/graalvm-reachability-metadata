/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.websocket_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.common.events.annotated.EventMethod;
import org.junit.jupiter.api.Test;

public class EventMethodTest {

    @Test
    public void resolvesNamedMethodAndInvokesIt() {
        RecordingEndpoint endpoint = new RecordingEndpoint();
        EventMethod eventMethod = new EventMethod(RecordingEndpoint.class, "onText", String.class);

        eventMethod.call(endpoint, "message");

        assertThat(eventMethod.getMethod()).isNotNull();
        assertThat(endpoint.messages).containsExactly("message");
    }

    @Test
    public void dropsLeadingArgumentWhenCallProvidesMoreArgumentsThanMethodSignature() {
        RecordingEndpoint endpoint = new RecordingEndpoint();
        EventMethod eventMethod = new EventMethod(RecordingEndpoint.class, "onText", String.class);

        eventMethod.call(endpoint, new Object(), "payload");

        assertThat(endpoint.messages).containsExactly("payload");
    }

    @Test
    public void identifiesSessionAndStreamingParametersFromResolvedMethod() {
        EventMethod sessionMethod = new EventMethod(RecordingEndpoint.class, "onSession", Session.class);
        EventMethod streamingMethod = new EventMethod(RecordingEndpoint.class, "onStream", InputStream.class);

        assertThat(sessionMethod.isHasSession()).isTrue();
        assertThat(sessionMethod.isStreaming()).isFalse();
        assertThat(streamingMethod.isHasSession()).isFalse();
        assertThat(streamingMethod.isStreaming()).isTrue();
    }

    public static class RecordingEndpoint {
        private final List<String> messages = new ArrayList<>();

        public void onText(String message) {
            messages.add(message);
        }

        public void onSession(Session session) {
            messages.add("session:" + session);
        }

        public void onStream(InputStream stream) {
            messages.add("stream:" + stream);
        }
    }
}
