/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.Conventions;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class FieldPropertyAccessorTest {
    @Test
    void decodesPrivateFieldWithoutSetterUsingSetPrivateFieldsConvention() {
        final BsonDocument document = new BsonDocument("label", new BsonString("decoded-private-field"));

        final PrivateFieldPojo decoded = decode(PrivateFieldPojo.class, document);

        assertThat(decoded.getLabel()).isEqualTo("decoded-private-field");
    }

    private static <T> T decode(final Class<T> type, final BsonDocument document) {
        final Codec<T> codec = codecFor(type);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    private static <T> Codec<T> codecFor(final Class<T> type) {
        final List<Convention> conventions = new ArrayList<>(Conventions.DEFAULT_CONVENTIONS);
        conventions.add(Conventions.SET_PRIVATE_FIELDS_CONVENTION);
        final PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder()
                .conventions(conventions)
                .register(type)
                .build();
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), pojoCodecProvider);
        return registry.get(type);
    }

    public static final class PrivateFieldPojo {
        private String label;

        public PrivateFieldPojo() {
        }

        public String getLabel() {
            return label;
        }
    }
}
