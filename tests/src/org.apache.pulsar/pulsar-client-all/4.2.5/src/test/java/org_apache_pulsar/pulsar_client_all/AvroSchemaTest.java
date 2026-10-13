/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.SchemaDefinition;
import org.apache.pulsar.common.schema.SchemaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AvroSchemaTest {
    @Test
    void roundTripsPojoWithJsr310AvroSchema() {
        SchemaDefinition<AuditEvent> definition = SchemaDefinition.<AuditEvent>builder()
                .withPojo(AuditEvent.class)
                .withAlwaysAllowNull(false)
                .withJSR310ConversionEnabled(true)
                .addProperty("domain", "audit")
                .build();
        Schema<AuditEvent> schema = Schema.AVRO(definition);
        AuditEvent event = new AuditEvent(
                "event-17", Instant.parse("2025-01-15T10:15:30.123Z"), 4);

        byte[] encoded = schema.encode(event);
        AuditEvent decoded = schema.decode(encoded);

        assertThat(encoded).isNotEmpty();
        assertThat(schema.getSchemaInfo().getType()).isEqualTo(SchemaType.AVRO);
        assertThat(schema.getSchemaInfo().getProperties()).containsEntry("domain", "audit");
        assertThat(decoded.getEventId()).isEqualTo(event.getEventId());
        assertThat(decoded.getOccurredAt()).isEqualTo(event.getOccurredAt());
        assertThat(decoded.getSeverity()).isEqualTo(event.getSeverity());
    }

    public static final class AuditEvent {
        private String eventId;
        private Instant occurredAt;
        private int severity;

        public AuditEvent() {}

        public AuditEvent(String eventId, Instant occurredAt, int severity) {
            this.eventId = eventId;
            this.occurredAt = occurredAt;
            this.severity = severity;
        }

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }

        public void setOccurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
        }

        public int getSeverity() {
            return severity;
        }

        public void setSeverity(int severity) {
            this.severity = severity;
        }
    }
}
