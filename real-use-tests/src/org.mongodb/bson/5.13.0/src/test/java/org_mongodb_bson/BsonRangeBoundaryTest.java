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
import org.bson.BsonMaxKey;
import org.bson.BsonMinKey;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises BSON range-boundary sentinels for an application's open-ended query. */
class BsonRangeBoundaryTest {

    @Test
    @Timeout(60)
    void preservesOpenEndedRangeBoundsAcrossStorage() {
        BsonDocument range = new BsonDocument()
                .append("sortKey", new BsonDocument()
                        .append("$gte", new BsonMinKey())
                        .append("$lte", new BsonMaxKey()));
        BsonDocumentCodec codec = new BsonDocumentCodec();

        BsonDocument restored = roundTrip(codec, range);
        BsonDocument restoredBounds = restored.getDocument("sortKey");
        BsonValue lowerBound = restoredBounds.get("$gte");
        BsonValue upperBound = restoredBounds.get("$lte");

        assertThat(lowerBound).isInstanceOf(BsonMinKey.class);
        assertThat(upperBound).isInstanceOf(BsonMaxKey.class);
        assertThat(lowerBound.getBsonType()).isEqualTo(BsonType.MIN_KEY);
        assertThat(upperBound.getBsonType()).isEqualTo(BsonType.MAX_KEY);
    }

    private static BsonDocument roundTrip(BsonDocumentCodec codec, BsonDocument document) {
        BsonDocument encoded = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(encoded);
        codec.encode(writer, document, EncoderContext.builder().build());
        writer.close();

        BsonDocumentReader reader = new BsonDocumentReader(encoded);
        BsonDocument decoded = codec.decode(reader, DecoderContext.builder().build());
        reader.close();
        return decoded;
    }
}
