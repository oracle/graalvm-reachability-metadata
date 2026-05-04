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
    void encodesPublicGetterBackedProperty() {
        final GetterBackedPojo value = new GetterBackedPojo("getter-value");

        final BsonDocument encoded = encode(GetterBackedPojo.class, value);

        assertThat(encoded.getString("name").getValue()).isEqualTo("getter-value");
    }

    @Test
    void encodesAndDecodesPublicFieldBackedProperty() {
        final PublicFieldPojo value = new PublicFieldPojo();
        value.fieldValue = "field-value";

        final BsonDocument encoded = encode(PublicFieldPojo.class, value);
        final PublicFieldPojo decoded = decode(PublicFieldPojo.class, encoded);

        assertThat(encoded.getString("fieldValue").getValue()).isEqualTo("field-value");
        assertThat(decoded.fieldValue).isEqualTo("field-value");
    }

    private static <T> BsonDocument encode(final Class<T> type, final T value) {
        final BsonDocument document = new BsonDocument();
        final Codec<T> codec = codec(type);
        codec.encode(new BsonDocumentWriter(document), value, EncoderContext.builder().build());
        return document;
    }

    private static <T> T decode(final Class<T> type, final BsonDocument document) {
        final Codec<T> codec = codec(type);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    private static <T> Codec<T> codec(final Class<T> type) {
        final PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder().register(type).build();
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), pojoCodecProvider);
        return registry.get(type);
    }

    public static final class GetterBackedPojo {
        private final String name;

        public GetterBackedPojo() {
            this("default");
        }

        public GetterBackedPojo(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final class PublicFieldPojo {
        public String fieldValue;
    }
}
