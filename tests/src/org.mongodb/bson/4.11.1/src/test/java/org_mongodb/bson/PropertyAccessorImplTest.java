/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class PropertyAccessorImplTest {
    @Test
    void encodesPojoThroughGetterAccessor() {
        final GetterBackedPojo pojo = new GetterBackedPojo("getter-value");

        final BsonDocument document = encode(pojo, GetterBackedPojo.class);

        assertThat(document.getString("name")).isEqualTo(new BsonString("getter-value"));
    }

    @Test
    void encodesPojoThroughPublicFieldAccessor() {
        final PublicFieldPojo pojo = new PublicFieldPojo();
        pojo.name = "field-value";
        pojo.quantity = 41;

        final BsonDocument document = encode(pojo, PublicFieldPojo.class);

        assertThat(document.getString("name")).isEqualTo(new BsonString("field-value"));
        assertThat(document.getInt32("quantity")).isEqualTo(new BsonInt32(41));
    }

    @Test
    void decodesPojoThroughPublicFieldAccessor() {
        final BsonDocument document = new BsonDocument()
                .append("name", new BsonString("decoded-field"))
                .append("quantity", new BsonInt32(42));

        final PublicFieldPojo pojo = decode(document, PublicFieldPojo.class);

        assertThat(pojo.name).isEqualTo("decoded-field");
        assertThat(pojo.quantity).isEqualTo(42);
    }

    private static <T> BsonDocument encode(final T pojo, final Class<T> pojoClass) {
        final BsonDocument document = new BsonDocument();
        final Codec<T> codec = registry(pojoClass).get(pojoClass);
        codec.encode(new BsonDocumentWriter(document), pojo, EncoderContext.builder().build());
        return document;
    }

    private static <T> T decode(final BsonDocument document, final Class<T> pojoClass) {
        final Codec<T> codec = registry(pojoClass).get(pojoClass);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    private static CodecRegistry registry(final Class<?> pojoClass) {
        return fromProviders(
                new ValueCodecProvider(),
                new BsonValueCodecProvider(),
                PojoCodecProvider.builder().register(pojoClass).build());
    }

    public static final class GetterBackedPojo {
        private String name;

        public GetterBackedPojo() {
        }

        GetterBackedPojo(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }

    public static final class PublicFieldPojo {
        public String name;
        public int quantity;

        public PublicFieldPojo() {
        }
    }
}
