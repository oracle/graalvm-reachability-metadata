/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.event.EventUtils;
import org.junit.jupiter.api.Test;

public class EventUtilsTest {

    @Test
    public void bindEventsToMethodRegistersProxyListenerAndForwardsMatchingEvents() {
        SampleEventSource eventSource = new SampleEventSource();
        RecordingTarget target = new RecordingTarget();

        EventUtils.bindEventsToMethod(target, "record", eventSource, SampleListener.class, "messageReceived");
        eventSource.publishMessage("alpha");
        eventSource.publishIgnored("beta");

        assertThat(eventSource.listenerCount()).isEqualTo(1);
        assertThat(target.messages()).containsExactly("alpha");
    }

    public interface SampleListener {
        void messageReceived(String message);

        void ignored(String message);
    }

    public static final class SampleEventSource {
        private final List<SampleListener> listeners = new ArrayList<SampleListener>();

        public void addSampleListener(final SampleListener listener) {
            listeners.add(listener);
        }

        public void publishMessage(final String message) {
            for (SampleListener listener : listeners) {
                listener.messageReceived(message);
            }
        }

        public void publishIgnored(final String message) {
            for (SampleListener listener : listeners) {
                listener.ignored(message);
            }
        }

        public int listenerCount() {
            return listeners.size();
        }
    }

    public static final class RecordingTarget {
        private final List<String> messages = new ArrayList<String>();

        public void record(final String message) {
            messages.add(message);
        }

        public List<String> messages() {
            return messages;
        }
    }
}
