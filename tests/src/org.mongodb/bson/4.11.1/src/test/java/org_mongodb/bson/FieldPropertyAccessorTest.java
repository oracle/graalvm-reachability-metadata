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
import static org.bson.codecs.pojo.Conventions.ANNOTATION_CONVENTION;
import static org.bson.codecs.pojo.Conventions.CLASS_AND_PROPERTY_CONVENTION;
import static org.bson.codecs.pojo.Conventions.OBJECT_ID_GENERATORS;
import static org.bson.codecs.pojo.Conventions.SET_PRIVATE_FIELDS_CONVENTION;

import java.util.Arrays;

import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

public class FieldPropertyAccessorTest {
    private static final CodecRegistry CODEC_REGISTRY = fromRegistries(
            fromProviders(new ValueCodecProvider()),
            fromProviders(PojoCodecProvider.builder()
                    .automatic(true)
                    .conventions(Arrays.asList(
                            CLASS_AND_PROPERTY_CONVENTION,
                            ANNOTATION_CONVENTION,
                            SET_PRIVATE_FIELDS_CONVENTION,
                            OBJECT_ID_GENERATORS))
                    .build()));

    @Test
    void decodesPojoPropertyThroughPrivateFieldWhenSetterIsAbsent() {
        GetterOnlyPojo decoded = decode(document("private-field-value"), GetterOnlyPojo.class);

        assertThat(decoded.getName()).isEqualTo("private-field-value");
    }

    private static BsonDocument document(String name) {
        BsonDocument document = new BsonDocument();
        document.append("name", new BsonString(name));
        return document;
    }

    private static <T> T decode(BsonDocument document, Class<T> type) {
        Codec<T> codec = CODEC_REGISTRY.get(type);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static class GetterOnlyPojo {
        private String name;

        public GetterOnlyPojo() {
        }

        public String getName() {
            return name;
        }
    }
}
