/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class CollectionPropertyCodecProviderInnerCollectionCodecTest {
    @Test
    void decodesConcreteCollectionPropertyUsingItsNoArgumentConstructor() {
        final BsonDocument document = new BsonDocument()
                .append("tags", new BsonArray(Arrays.asList(new BsonString("red"), new BsonString("blue"))));

        final CollectionPropertyPojo pojo = decode(document, CollectionPropertyPojo.class);

        assertThat(pojo.getTags()).isInstanceOf(ConstructorBackedCollection.class);
        assertThat(pojo.getTags()).containsExactly("red", "blue");
    }

    private static <T> T decode(final BsonDocument document, final Class<T> pojoClass) {
        final CodecRegistry registry = fromProviders(
                new ValueCodecProvider(),
                new BsonValueCodecProvider(),
                PojoCodecProvider.builder().register(pojoClass).build());
        final Codec<T> codec = registry.get(pojoClass);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static final class CollectionPropertyPojo {
        private ConstructorBackedCollection<String> tags;

        public CollectionPropertyPojo() {
        }

        public ConstructorBackedCollection<String> getTags() {
            return tags;
        }

        public void setTags(final ConstructorBackedCollection<String> tags) {
            this.tags = tags;
        }
    }

    public static final class ConstructorBackedCollection<T> extends ArrayList<T> {
        public ConstructorBackedCollection() {
        }
    }
}
