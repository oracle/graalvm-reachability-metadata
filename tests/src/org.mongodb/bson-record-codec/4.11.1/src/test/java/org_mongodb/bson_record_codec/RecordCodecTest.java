/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson_record_codec;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.record.RecordCodecProvider;
import org.bson.codecs.record.annotations.BsonId;
import org.bson.codecs.record.annotations.BsonProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class RecordCodecTest {
    @Test
    void decodesDocumentByInvokingTheRecordCanonicalConstructor() {
        final Codec<DecodedRecord> codec = codecFor(DecodedRecord.class);
        final BsonDocument document = new BsonDocument()
                .append("_id", new BsonString("abc-123"))
                .append("full_name", new BsonString("Ada"))
                .append("quantity", new BsonInt32(7))
                .append("unknown", new BsonString("ignored"));

        final DecodedRecord decoded = codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());

        assertThat(decoded).isEqualTo(new DecodedRecord("abc-123", "Ada", 7));
    }

    private static <T> Codec<T> codecFor(final Class<T> type) {
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), new RecordCodecProvider());
        return registry.get(type);
    }

    public record DecodedRecord(
            @BsonId String id,
            @BsonProperty("full_name") String name,
            int quantity) {
    }
}
