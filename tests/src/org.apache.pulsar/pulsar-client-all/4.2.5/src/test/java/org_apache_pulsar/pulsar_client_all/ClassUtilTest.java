/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class ClassUtilTest {

    @Test
    void mapsBeansRecordsEnumsAndArraysThroughJackson() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final Envelope source = new Envelope();
        source.setName("orders");
        source.setState(State.READY);
        source.setEvents(List.of(new Event("created", 3)));

        final String json = mapper.writeValueAsString(source);
        final Envelope restored = mapper.readValue(json, Envelope.class);
        final Event record = mapper.readValue("{\"name\":\"stored\",\"count\":5}", Event.class);
        final State[] states = mapper.readValue("[\"READY\",\"DONE\"]", State[].class);

        assertThat(restored.getName()).isEqualTo("orders");
        assertThat(restored.getState()).isEqualTo(State.READY);
        assertThat(restored.getEvents()).containsExactly(new Event("created", 3));
        assertThat(record).isEqualTo(new Event("stored", 5));
        assertThat(states).containsExactly(State.READY, State.DONE);
    }

    public enum State {
        READY,
        DONE
    }

    public record Event(String name, int count) {
    }

    public static final class Envelope {
        private String name;
        private State state;
        private List<Event> events;

        public Envelope() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public State getState() {
            return state;
        }

        public void setState(State state) {
            this.state = state;
        }

        public List<Event> getEvents() {
            return events;
        }

        public void setEvents(List<Event> events) {
            this.events = events;
        }
    }
}
