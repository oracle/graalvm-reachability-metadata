/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.pulsar.shade.org.apache.commons.lang3.event.EventListenerSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class EventListenerSupportTest {
    @Test
    void forwardsEventsToAllRegisteredListeners() {
        EventListenerSupport<StateListener> support =
                EventListenerSupport.create(StateListener.class);
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        support.addListener(first::add);
        support.addListener(second::add);

        support.fire().stateChanged("active");

        assertThat(first).containsExactly("active");
        assertThat(second).containsExactly("active");
        assertThat(support.getListeners()).hasSize(2);
    }

    public interface StateListener {
        void stateChanged(String state);
    }
}
