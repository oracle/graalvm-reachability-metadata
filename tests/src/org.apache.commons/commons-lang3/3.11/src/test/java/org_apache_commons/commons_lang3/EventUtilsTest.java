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
    void bindsSelectedEventMethodsToATargetMethod() {
        final SampleEventSource eventSource = new SampleEventSource();
        final RecordingTarget target = new RecordingTarget();

        EventUtils.bindEventsToMethod(target, "record", eventSource, SampleListener.class, "onValueChanged");

        eventSource.fireValueChanged("alpha");
        eventSource.fireIgnored("beta");

        assertThat(eventSource.listenerCount()).isEqualTo(1);
        assertThat(target.recordedValues()).containsExactly("alpha");
    }

    @Test
    void fallsBackToNoArgTargetMethodWhenListenerParametersDoNotMatch() {
        final SampleEventSource eventSource = new SampleEventSource();
        final NoArgTarget target = new NoArgTarget();

        EventUtils.bindEventsToMethod(target, "markTriggered", eventSource, SampleListener.class);

        eventSource.fireValueChanged("alpha");

        assertThat(target.invocationCount()).isEqualTo(1);
    }

    public interface SampleListener {
        void onValueChanged(String value);

        void onIgnored(String value);
    }

    public static final class SampleEventSource {
        private final List<SampleListener> listeners = new ArrayList<>();

        public void addSampleListener(final SampleListener listener) {
            listeners.add(listener);
        }

        public void fireValueChanged(final String value) {
            for (final SampleListener listener : listeners) {
                listener.onValueChanged(value);
            }
        }

        public void fireIgnored(final String value) {
            for (final SampleListener listener : listeners) {
                listener.onIgnored(value);
            }
        }

        public int listenerCount() {
            return listeners.size();
        }
    }

    public static final class RecordingTarget {
        private final List<String> recordedValues = new ArrayList<>();

        public void record(final String value) {
            recordedValues.add(value);
        }

        public List<String> recordedValues() {
            return recordedValues;
        }
    }

    public static final class NoArgTarget {
        private int invocationCount;

        public void markTriggered() {
            invocationCount++;
        }

        public int invocationCount() {
            return invocationCount;
        }
    }
}
