/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson_record_codec;

import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.codecs.record.RecordCodecProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class RecordCodecInnerComponentModelTest {
    @Test
    void encodesRecordUsingPojoAnnotationsFoundOnRecordFields() {
        final Codec<AnnotatedRecord> codec = codecFor(AnnotatedRecord.class);
        final BsonDocument document = new BsonDocument();

        codec.encode(new BsonDocumentWriter(document), new AnnotatedRecord("abc-123", "Ada", 7),
                EncoderContext.builder().build());

        assertThat(document.getString("_id").getValue()).isEqualTo("abc-123");
        assertThat(document.getString("full_name").getValue()).isEqualTo("Ada");
        assertThat(document.getInt32("quantity").getValue()).isEqualTo(7);
    }

    private static <T> Codec<T> codecFor(final Class<T> type) {
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), new RecordCodecProvider());
        return registry.get(type);
    }

    public record AnnotatedRecord(
            @BsonId String id,
            @BsonProperty("full_name") String name,
            int quantity) {
    }
}
