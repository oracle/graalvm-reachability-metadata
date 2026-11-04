/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.codecs.Codec;
import org.bson.codecs.CollectionCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.UuidCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises BSON UUID codecs to persist and restore an application event. */
class BsonUuidEventTest {

    @Test
    @Timeout(60)
    void persistsAnEventIdUsingTheStandardUuidRepresentation() {
        UUID eventId = UUID.fromString("4f8d2c91-2b8a-4b0f-9f35-5d2c7e1a6b40");
        Document event = new Document("eventId", eventId)
                .append("type", "shipment.created")
                .append("payload", new Document("shipmentNumber", "SHP-2048"));
        CodecRegistry registry = CodecRegistries.fromProviders(
                new UuidCodecProvider(UuidRepresentation.STANDARD), new ValueCodecProvider(),
                new DocumentCodecProvider(), new CollectionCodecProvider());
        Codec<Document> documentCodec = new DocumentCodec(registry)
                .withUuidRepresentation(UuidRepresentation.STANDARD);

        BsonDocument stored = encode(documentCodec, event);
        Document restored = decode(documentCodec, stored);

        assertThat(stored.getBinary("eventId").getType())
                .isEqualTo(BsonBinarySubType.UUID_STANDARD.getValue());
        assertThat(restored.get("eventId")).isEqualTo(eventId);
        assertThat(restored.getString("type")).isEqualTo("shipment.created");
        assertThat(restored.getEmbedded(List.of("payload", "shipmentNumber"), String.class))
                .isEqualTo("SHP-2048");
    }

    private static BsonDocument encode(Codec<Document> codec, Document value) {
        BsonDocument target = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(target);
        codec.encode(writer, value, EncoderContext.builder().build());
        writer.close();
        return target;
    }

    private static Document decode(Codec<Document> codec, BsonDocument value) {
        BsonDocumentReader reader = new BsonDocumentReader(value);
        Document result = codec.decode(reader, DecoderContext.builder().build());
        reader.close();
        return result;
    }
}
