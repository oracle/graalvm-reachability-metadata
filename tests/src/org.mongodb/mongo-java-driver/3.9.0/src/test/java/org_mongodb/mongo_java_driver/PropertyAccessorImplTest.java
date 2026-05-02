/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.MongoClient;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class PropertyAccessorImplTest {
    @Test
    void encodesPojoPropertyThroughGetter() {
        final GetterPojo pojo = new GetterPojo();
        pojo.setName("getter-value");

        final BsonDocument document = encode(GetterPojo.class, pojo);

        assertThat(document.getString("name").getValue()).isEqualTo("getter-value");
    }

    @Test
    void encodesAndDecodesPublicFieldProperties() {
        final PublicFieldPojo pojo = new PublicFieldPojo();
        pojo.name = "field-value";
        pojo.count = 42;

        final BsonDocument document = encode(PublicFieldPojo.class, pojo);
        final PublicFieldPojo decoded = decode(PublicFieldPojo.class, document);

        assertThat(document.getString("name").getValue()).isEqualTo("field-value");
        assertThat(document.getInt32("count").getValue()).isEqualTo(42);
        assertThat(decoded.name).isEqualTo("field-value");
        assertThat(decoded.count).isEqualTo(42);
    }

    private static <T> BsonDocument encode(final Class<T> pojoClass, final T value) {
        final BsonDocument document = new BsonDocument();
        codecFor(pojoClass).encode(new BsonDocumentWriter(document), value, EncoderContext.builder().build());
        return document;
    }

    private static <T> T decode(final Class<T> pojoClass, final BsonDocument document) {
        return codecFor(pojoClass).decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    private static <T> Codec<T> codecFor(final Class<T> pojoClass) {
        final CodecRegistry registry = fromRegistries(
                MongoClient.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().register(pojoClass).build()));
        return registry.get(pojoClass);
    }

    public static class GetterPojo {
        private String name;

        public GetterPojo() {
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }

    public static class PublicFieldPojo {
        public String name;
        public int count;

        public PublicFieldPojo() {
        }
    }
}
