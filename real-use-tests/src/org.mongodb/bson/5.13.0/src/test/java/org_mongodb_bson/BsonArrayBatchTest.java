/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BsonArray;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.codecs.BsonArrayCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises BSON arrays for an application's ordered bulk-notification batch. */
class BsonArrayBatchTest {

    @Test
    @Timeout(60)
    void preservesBatchOrderAndPayloadTypesAcrossStorage() {
        BsonArray submitted = BsonArray.parse("""
                [
                  {"kind": "shipment.created", "sequence": {"$numberLong": "41"},
                   "urgent": true},
                  {"kind": "shipment.updated", "sequence": {"$numberLong": "42"},
                   "urgent": false}
                ]
                """);
        BsonArrayCodec codec = new BsonArrayCodec();

        byte[] encoded = encode(codec, submitted);
        BsonArray restored = decode(codec, encoded);
        BsonDocument first = restored.get(0).asDocument();
        BsonDocument second = restored.get(1).asDocument();

        assertThat(encoded).isNotEmpty();
        assertThat(restored).hasSize(2);
        assertThat(first.getString("kind").getValue()).isEqualTo("shipment.created");
        assertThat(first.getInt64("sequence").getValue()).isEqualTo(41L);
        assertThat(first.getBoolean("urgent").getValue()).isTrue();
        assertThat(second.getString("kind").getValue()).isEqualTo("shipment.updated");
        assertThat(second.getInt64("sequence").getValue()).isEqualTo(42L);
        assertThat(second.getBoolean("urgent").getValue()).isFalse();
    }

    private static byte[] encode(BsonArrayCodec codec, BsonArray value) {
        try (BasicOutputBuffer output = new BasicOutputBuffer()) {
            BsonBinaryWriter writer = new BsonBinaryWriter(output);
            writer.writeStartDocument();
            writer.writeName("events");
            codec.encode(writer, value, EncoderContext.builder().build());
            writer.writeEndDocument();
            writer.close();
            return output.toByteArray();
        }
    }

    private static BsonArray decode(BsonArrayCodec codec, byte[] encoded) {
        try (BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(encoded))) {
            reader.readStartDocument();
            reader.readName();
            BsonArray result = codec.decode(reader, DecoderContext.builder().build());
            reader.readEndDocument();
            return result;
        }
    }
}
