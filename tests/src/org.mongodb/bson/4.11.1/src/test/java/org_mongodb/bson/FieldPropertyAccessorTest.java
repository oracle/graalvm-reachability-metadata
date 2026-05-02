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
import org.bson.codecs.BsonValueCodecProvider;
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
    void decodesPojoBySettingPrivateFieldWhenNoSetterExists() {
        final BsonDocument document = new BsonDocument("name", new BsonString("decoded-private-field"));

        final PrivateFieldPojo pojo = decode(document, PrivateFieldPojo.class);

        assertThat(pojo.getName()).isEqualTo("decoded-private-field");
    }

    private static <T> T decode(final BsonDocument document, final Class<T> pojoClass) {
        final Codec<T> codec = registry(pojoClass).get(pojoClass);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    private static CodecRegistry registry(final Class<?> pojoClass) {
        final List<Convention> conventions = new ArrayList<>(Conventions.DEFAULT_CONVENTIONS);
        conventions.add(Conventions.SET_PRIVATE_FIELDS_CONVENTION);
        return fromProviders(
                new ValueCodecProvider(),
                new BsonValueCodecProvider(),
                PojoCodecProvider.builder().conventions(conventions).register(pojoClass).build());
    }

    public static final class PrivateFieldPojo {
        private String name;

        public PrivateFieldPojo() {
        }

        public String getName() {
            return name;
        }
    }
}
