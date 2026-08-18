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
    public void bindEventsToMethodAddsAProxyListenerThatForwardsEventArguments() {
        SampleEventSource eventSource = new SampleEventSource();
        RecordingTarget target = new RecordingTarget();

        EventUtils.bindEventsToMethod(target, "recordMessage", eventSource, SampleListener.class);

        assertThat(eventSource.listenerCount()).isEqualTo(1);

        eventSource.fireTextReceived("payload");

        assertThat(target.messages()).containsExactly("payload");
    }

    @Test
    public void bindEventsToMethodCanFilterEventTypesAndFallBackToNoArgTargetMethods() {
        SampleEventSource eventSource = new SampleEventSource();
        RecordingTarget target = new RecordingTarget();

        EventUtils.bindEventsToMethod(
                target,
                "recordStatusChange",
                eventSource,
                SampleListener.class,
                "statusChanged");

        eventSource.fireTextReceived("ignored");
        eventSource.fireStatusChanged(200);

        assertThat(target.messages()).isEmpty();
        assertThat(target.statusChangeCount()).isEqualTo(1);
    }

    public interface SampleListener {

        void textReceived(String message);

        void statusChanged(int statusCode);
    }

    public static final class SampleEventSource {

        private final List<SampleListener> listeners = new ArrayList<>();

        public void addSampleListener(final SampleListener listener) {
            listeners.add(listener);
        }

        public void fireTextReceived(final String message) {
            for (SampleListener listener : listeners) {
                listener.textReceived(message);
            }
        }

        public void fireStatusChanged(final int statusCode) {
            for (SampleListener listener : listeners) {
                listener.statusChanged(statusCode);
            }
        }

        public int listenerCount() {
            return listeners.size();
        }
    }

    public static final class RecordingTarget {

        private final List<String> messages = new ArrayList<>();
        private int statusChangeCount;

        public void recordMessage(final String message) {
            messages.add(message);
        }

        public void recordStatusChange() {
            statusChangeCount++;
        }

        public List<String> messages() {
            return messages;
        }

        public int statusChangeCount() {
            return statusChangeCount;
        }
    }
}
