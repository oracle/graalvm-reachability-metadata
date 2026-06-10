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

import org.apache.commons.lang3.event.EventListenerSupport;
import org.junit.jupiter.api.Test;

public class EventListenerSupportProxyInvocationHandlerTest {

    @Test
    public void fireDispatchesListenerCallsToEachRegisteredListener() {
        EventListenerSupport<SampleListener> listenerSupport = EventListenerSupport.create(SampleListener.class);
        RecordingSampleListener firstListener = new RecordingSampleListener("first");
        RecordingSampleListener secondListener = new RecordingSampleListener("second");

        listenerSupport.addListener(firstListener);
        listenerSupport.addListener(secondListener);

        listenerSupport.fire().onEvent("payload");

        assertThat(firstListener.deliveries()).containsExactly("first:payload");
        assertThat(secondListener.deliveries()).containsExactly("second:payload");
    }

    public interface SampleListener {

        void onEvent(String message);
    }

    public static final class RecordingSampleListener implements SampleListener {

        private final String name;
        private final List<String> deliveries = new ArrayList<>();

        public RecordingSampleListener(final String name) {
            this.name = name;
        }

        @Override
        public void onEvent(final String message) {
            deliveries.add(name + ":" + message);
        }

        public List<String> deliveries() {
            return deliveries;
        }
    }
}
