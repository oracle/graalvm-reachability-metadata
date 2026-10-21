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

public class EventListenerSupportInnerProxyInvocationHandlerTest {
    @Test
    void fireProxyDispatchesToEveryListener() {
        EventListenerSupport<UpdateListener> support = EventListenerSupport.create(UpdateListener.class);
        List<String> updates = new ArrayList<>();
        support.addListener(value -> updates.add("first:" + value));
        support.addListener(value -> updates.add("second:" + value));

        support.fire().updated("ready");

        assertThat(updates).containsExactly("first:ready", "second:ready");
    }

    public interface UpdateListener {
        void updated(String value);
    }
}
