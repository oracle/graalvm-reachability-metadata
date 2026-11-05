/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonString;
import org.bson.BsonTimestamp;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises BSON timestamps for an application's ordered change-feed checkpoint. */
class BsonTimestampCheckpointTest {

    @Test
    @Timeout(60)
    void roundTripsAChangeFeedCheckpointAndComparesItsPosition() {
        BsonTimestamp previous = new BsonTimestamp(1_742_304_000, 12);
        BsonTimestamp checkpoint = new BsonTimestamp(1_742_304_001, 3);
        BsonDocument change = new BsonDocument()
                .append("operation", new BsonString("shipment.updated"))
                .append("clusterTime", checkpoint);
        BsonDocumentCodec codec = new BsonDocumentCodec();

        BsonDocument stored = encode(codec, change);
        BsonDocument restored = decode(codec, stored);
        BsonTimestamp restoredCheckpoint = restored.getTimestamp("clusterTime");

        assertThat(restoredCheckpoint.getBsonType()).isEqualTo(checkpoint.getBsonType());
        assertThat(restoredCheckpoint.getTime()).isEqualTo(1_742_304_001);
        assertThat(restoredCheckpoint.getInc()).isEqualTo(3);
        assertThat(restoredCheckpoint).isGreaterThan(previous);
        assertThat(restored.getString("operation").getValue()).isEqualTo("shipment.updated");
    }

    private static BsonDocument encode(BsonDocumentCodec codec, BsonDocument value) {
        BsonDocument stored = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(stored);
        codec.encode(writer, value, EncoderContext.builder().build());
        writer.close();
        return stored;
    }

    private static BsonDocument decode(BsonDocumentCodec codec, BsonDocument value) {
        BsonDocumentReader reader = new BsonDocumentReader(value);
        BsonDocument result = codec.decode(reader, DecoderContext.builder().build());
        reader.close();
        return result;
    }
}
