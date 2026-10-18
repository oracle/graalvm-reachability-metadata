/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.EventListener;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.apache.pulsar.shade.org.apache.commons.lang3.event.EventListenerSupport;
import org.junit.jupiter.api.Test;

public class EventListenerSupportTest {

    @Test
    void dispatchesEventsBeforeAndAfterSerializationRoundTrip() {
        final EventListenerSupport<ChangeListener> support = EventListenerSupport.create(ChangeListener.class);
        final CountingListener listener = new CountingListener();
        support.addListener(listener);

        support.fire().changed(3);
        final EventListenerSupport<ChangeListener> restored = SerializationUtils.clone(support);
        restored.fire().changed(4);

        assertThat(listener.total).isEqualTo(3);
        assertThat(((CountingListener) restored.getListeners()[0]).total).isEqualTo(7);
    }

    public interface ChangeListener extends EventListener, Serializable {
        void changed(int amount);
    }

    public static final class CountingListener implements ChangeListener {
        private static final long serialVersionUID = 1L;
        private int total;

        @Override
        public void changed(int amount) {
            total += amount;
        }
    }
}
