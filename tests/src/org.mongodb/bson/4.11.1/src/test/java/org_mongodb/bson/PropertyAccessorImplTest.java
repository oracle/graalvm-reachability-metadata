/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

public class PropertyAccessorImplTest {
    private static final CodecRegistry CODEC_REGISTRY = fromRegistries(
            fromProviders(new ValueCodecProvider()),
            fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    @Test
    void encodesPojoPropertyThroughGetter() {
        GetterBackedPojo pojo = new GetterBackedPojo("getter-value");

        BsonDocument encoded = encode(pojo, GetterBackedPojo.class);

        assertThat(encoded.getString("name").getValue()).isEqualTo("getter-value");
    }

    @Test
    void encodesAndDecodesPojoPropertyThroughPublicField() {
        PublicFieldBackedPojo pojo = new PublicFieldBackedPojo();
        pojo.name = "field-value";

        BsonDocument encoded = encode(pojo, PublicFieldBackedPojo.class);
        PublicFieldBackedPojo decoded = decode(document("decoded-field-value"), PublicFieldBackedPojo.class);

        assertThat(encoded.getString("name").getValue()).isEqualTo("field-value");
        assertThat(decoded.name).isEqualTo("decoded-field-value");
    }

    private static BsonDocument document(String name) {
        BsonDocument document = new BsonDocument();
        document.append("name", new BsonString(name));
        return document;
    }

    private static <T> BsonDocument encode(T value, Class<T> type) {
        BsonDocument document = new BsonDocument();
        Codec<T> codec = CODEC_REGISTRY.get(type);
        codec.encode(new BsonDocumentWriter(document), value, EncoderContext.builder().build());
        return document;
    }

    private static <T> T decode(BsonDocument document, Class<T> type) {
        Codec<T> codec = CODEC_REGISTRY.get(type);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static class GetterBackedPojo {
        private String name;

        public GetterBackedPojo() {
        }

        public GetterBackedPojo(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class PublicFieldBackedPojo {
        public String name;
    }
}
