/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.CollectionCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.io.BasicOutputBuffer;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises BSON document codecs to archive and restore an order received as Extended JSON. */
class BsonOrderArchiveTest {

    private static final ObjectId ORDER_ID = new ObjectId("65cbd4000000000000000042");

    @Test
    @Timeout(60)
    void archivesAnOrderWithoutLosingBsonTypes() {
        Document incoming = Document.parse("""
                {
                  "_id": {"$oid": "65cbd4000000000000000042"},
                  "customer": {"id": "customer-17", "tier": "gold"},
                  "items": [
                    {"sku": "tea-21", "quantity": {"$numberInt": "2"},
                     "unitPrice": {"$numberDecimal": "19.95"}},
                    {"sku": "mug-04", "quantity": {"$numberInt": "1"},
                     "unitPrice": {"$numberDecimal": "12.50"}}
                  ],
                  "placedAt": {"$date": "2025-02-14T09:30:00Z"},
                  "receipt": {"$binary": {"base64": "AQIDBA==", "subType": "00"}}
                }
                """);
        CodecRegistry registry = CodecRegistries.fromProviders(
                new ValueCodecProvider(), new BsonValueCodecProvider(), new DocumentCodecProvider(),
                new CollectionCodecProvider());
        Codec<Document> documentCodec = registry.get(Document.class);

        byte[] archived = encode(documentCodec, incoming);
        Document restored = decode(documentCodec, archived);

        assertThat(archived).isNotEmpty();
        assertThat(restored.getObjectId("_id")).isEqualTo(ORDER_ID);
        assertThat(restored.getEmbedded(List.of("customer", "tier"), String.class))
                .isEqualTo("gold");
        List<Document> items = restored.getList("items", Document.class);
        assertThat(items).extracting(item -> item.getString("sku"))
                .containsExactly("tea-21", "mug-04");
        assertThat(items.get(0).getInteger("quantity")).isEqualTo(2);
        assertThat(items.get(0).get("unitPrice")).isEqualTo(Decimal128.parse("19.95"));
        assertThat(restored.getDate("placedAt").toInstant())
                .isEqualTo(Instant.parse("2025-02-14T09:30:00Z"));
        assertThat(restored.get("receipt", org.bson.types.Binary.class).getData())
                .containsExactly(1, 2, 3, 4);
    }

    private static byte[] encode(Codec<Document> codec, Document document) {
        try (BasicOutputBuffer output = new BasicOutputBuffer()) {
            BsonBinaryWriter writer = new BsonBinaryWriter(output);
            codec.encode(writer, document, EncoderContext.builder().build());
            writer.close();
            return output.toByteArray();
        }
    }

    private static Document decode(Codec<Document> codec, byte[] archived) {
        try (BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(archived))) {
            return codec.decode(reader, DecoderContext.builder().build());
        }
    }
}
