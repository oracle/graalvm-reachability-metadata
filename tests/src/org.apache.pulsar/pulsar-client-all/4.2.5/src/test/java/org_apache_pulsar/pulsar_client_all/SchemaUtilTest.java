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

public class SchemaUtilTest {

    @Test
    void roundTripsPojoAndRecordSchemas() {
        final Schema<Payload> avro = Schema.AVRO(Payload.class);
        final Payload avroValue = avro.decode(avro.encode(new Payload("avro", 7)));

        final Schema<Event> json = Schema.JSON(Event.class);
        final Event jsonValue = json.decode(json.encode(new Event("json", 11)));

        assertThat(avro.getSchemaInfo().getSchema()).isNotEmpty();
        assertThat(avroValue.getName()).isEqualTo("avro");
        assertThat(avroValue.getSequence()).isEqualTo(7);
        assertThat(json.getSchemaInfo().getSchema()).isNotEmpty();
        assertThat(jsonValue).isEqualTo(new Event("json", 11));
    }

    public static final class Payload {
        private String name;
        private int sequence;

        public Payload() {
        }

        public Payload(String name, int sequence) {
            this.name = name;
            this.sequence = sequence;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getSequence() {
            return sequence;
        }

        public void setSequence(int sequence) {
            this.sequence = sequence;
        }
    }

    public record Event(String name, int sequence) {
    }
}
