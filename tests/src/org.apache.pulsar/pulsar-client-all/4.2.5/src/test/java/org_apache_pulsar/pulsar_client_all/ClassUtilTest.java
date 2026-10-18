/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.util.ClassUtil;
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

    @Test
    @SuppressWarnings("unchecked")
    void exposesDeclaredMembersAndAnnotatedEnumConstants() {
        final Class<Enum<?>> enumType = (Class<Enum<?>>) (Class<?>) State.class;

        assertThat(Arrays.stream(ClassUtil.getDeclaredFields(Envelope.class))
                        .map(field -> field.getName()))
                .contains("name", "state", "events");
        assertThat(Arrays.stream(ClassUtil.getDeclaredMethods(Envelope.class))
                        .map(method -> method.getName()))
                .contains("getName", "setName");
        assertThat(Arrays.stream(ClassUtil.getClassMethods(Envelope.class))
                        .map(method -> method.getName()))
                .contains("getName", "setEvents");
        assertThat(ClassUtil.findFirstAnnotatedEnumValue(enumType, Primary.class)).isEqualTo(State.READY);
    }

    public enum State {
        @Primary
        READY,
        DONE
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Primary {
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
