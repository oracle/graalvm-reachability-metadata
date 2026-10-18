/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.api.Schema;
import org.junit.jupiter.api.Test;

public class ReflectDatumReaderTest {

    @Test
    void reconstructsPojoArrayFieldsFromAvroData() {
        final Schema<ArrayPayload> schema = Schema.AVRO(ArrayPayload.class);
        final ArrayPayload source = new ArrayPayload("orders", new String[] {"created", "stored"});

        final ArrayPayload restored = schema.decode(schema.encode(source));

        assertThat(restored.getTopic()).isEqualTo("orders");
        assertThat(restored.getEvents()).containsExactly("created", "stored");
        assertThat(restored.getEvents().getClass()).isEqualTo(String[].class);
    }

    public static final class ArrayPayload {
        private String topic;
        private String[] events;

        public ArrayPayload() {
        }

        public ArrayPayload(String topic, String[] events) {
            this.topic = topic;
            this.events = events;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String[] getEvents() {
            return events;
        }

        public void setEvents(String[] events) {
            this.events = events;
        }
    }
}
