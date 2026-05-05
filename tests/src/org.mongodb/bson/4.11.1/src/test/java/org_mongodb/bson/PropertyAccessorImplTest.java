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
    void encodesValueReadThroughGetterMethod() {
        final GetterBackedPojo pojo = new GetterBackedPojo("getter-value");

        final BsonDocument encoded = encode(GetterBackedPojo.class, pojo);

        assertThat(encoded.getString("value").getValue()).isEqualTo("getter-value");
    }

    @Test
    void encodesAndDecodesValuesReadFromAndWrittenToPublicFields() {
        final PublicFieldPojo pojo = new PublicFieldPojo();
        pojo.label = "field-value";
        pojo.amount = 19;

        final BsonDocument encoded = encode(PublicFieldPojo.class, pojo);

        assertThat(encoded.getString("label").getValue()).isEqualTo("field-value");
        assertThat(encoded.getInt32("amount").getValue()).isEqualTo(19);

        final BsonDocument document = new BsonDocument()
                .append("label", new BsonString("decoded-field"))
                .append("amount", new BsonInt32(37));

        final PublicFieldPojo decoded = decode(PublicFieldPojo.class, document);

        assertThat(decoded.label).isEqualTo("decoded-field");
        assertThat(decoded.amount).isEqualTo(37);
    }

    private static <T> BsonDocument encode(final Class<T> type, final T value) {
        final BsonDocument document = new BsonDocument();
        final Codec<T> codec = codecFor(type);
        codec.encode(new BsonDocumentWriter(document), value, EncoderContext.builder().build());
        return document;
    }

    private static <T> T decode(final Class<T> type, final BsonDocument document) {
        final Codec<T> codec = codecFor(type);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    private static <T> Codec<T> codecFor(final Class<T> type) {
        final PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder().register(type).build();
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), pojoCodecProvider);
        return registry.get(type);
    }

    public static final class GetterBackedPojo {
        private String value;

        public GetterBackedPojo() {
        }

        GetterBackedPojo(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class PublicFieldPojo {
        public String label;
        public int amount;

        public PublicFieldPojo() {
        }
    }
}
