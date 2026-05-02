/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import com.mongodb.MongoClient;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class CollectionPropertyCodecProviderInnerCollectionCodecTest {
    @Test
    void decodesPojoConcreteCollectionProperty() {
        final BsonDocument document = new BsonDocument("tags", new BsonArray(
                Arrays.asList(new BsonString("alpha"), new BsonString("beta"))));

        final ConcreteCollectionPojo pojo = decode(ConcreteCollectionPojo.class, document);

        assertThat(pojo.tags).isInstanceOf(ArrayList.class);
        assertThat(pojo.tags).containsExactly("alpha", "beta");
    }

    private static <T> T decode(final Class<T> pojoClass, final BsonDocument document) {
        final CodecRegistry registry = fromRegistries(
                MongoClient.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().register(pojoClass).build()));
        final Codec<T> codec = registry.get(pojoClass);
        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static class ConcreteCollectionPojo {
        public ArrayList<String> tags;

        public ConcreteCollectionPojo() {
        }
    }
}
