/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ReflectionHelperInnerRecordSupportedHelperTest {
    @Test
    void mapsRecordsEnumsArraysAndMutableFields() {
        Gson gson = new Gson();
        Delivery delivery = new Delivery("route-7", State.READY, new String[] {"cold", "priority"});
        MutableStop mutableStop = new MutableStop("warehouse", 2);

        String deliveryJson = gson.toJson(delivery);
        Delivery decodedDelivery = gson.fromJson(deliveryJson, Delivery.class);
        MutableStop decodedStop = gson.fromJson(gson.toJson(mutableStop), MutableStop.class);

        assertThat(decodedDelivery.route()).isEqualTo("route-7");
        assertThat(decodedDelivery.state()).isEqualTo(State.READY);
        assertThat(decodedDelivery.tags()).containsExactly("cold", "priority");
        assertThat(decodedStop.name).isEqualTo("warehouse");
        assertThat(decodedStop.sequence).isEqualTo(2);
    }

    public enum State {
        READY,
        DELIVERED
    }

    public record Delivery(String route, State state, String[] tags) { }

    public static final class MutableStop {
        private String name;
        private int sequence;

        public MutableStop() { }

        public MutableStop(String name, int sequence) {
            this.name = name;
            this.sequence = sequence;
        }
    }
}
