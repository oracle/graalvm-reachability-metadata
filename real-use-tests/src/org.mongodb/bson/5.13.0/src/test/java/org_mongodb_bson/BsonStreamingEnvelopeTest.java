/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonType;
import org.bson.io.BasicOutputBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises streaming BSON readers and writers for routing an event envelope. */
class BsonStreamingEnvelopeTest {

    @Test
    @Timeout(60)
    void routesAnEventByItsEnvelopeWithoutDecodingItsPayload() {
        byte[] wireEvent = encodeEvent();

        EventEnvelope envelope = readEnvelope(wireEvent);

        assertThat(envelope.kind()).isEqualTo("shipment.updated");
        assertThat(envelope.sequence()).isEqualTo(42);
    }

    private static byte[] encodeEvent() {
        try (BasicOutputBuffer output = new BasicOutputBuffer()) {
            BsonBinaryWriter writer = new BsonBinaryWriter(output);
            writer.writeStartDocument();
            writer.writeString("kind", "shipment.updated");
            writer.writeInt64("sequence", 42L);
            writer.writeName("payload");
            writer.writeStartDocument();
            writer.writeString("shipmentNumber", "SHP-2048");
            writer.writeName("items");
            writer.writeStartArray();
            writer.writeString("PKG-204");
            writer.writeString("PKG-205");
            writer.writeEndArray();
            writer.writeEndDocument();
            writer.writeEndDocument();
            writer.close();
            return output.toByteArray();
        }
    }

    private static EventEnvelope readEnvelope(byte[] wireEvent) {
        String kind = null;
        long sequence = 0L;
        try (BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(wireEvent))) {
            reader.readStartDocument();
            while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                String fieldName = reader.readName();
                switch (fieldName) {
                    case "kind" -> kind = reader.readString();
                    case "sequence" -> sequence = reader.readInt64();
                    default -> reader.skipValue();
                }
            }
            reader.readEndDocument();
        }
        return new EventEnvelope(kind, sequence);
    }

    private record EventEnvelope(String kind, long sequence) {
    }
}
