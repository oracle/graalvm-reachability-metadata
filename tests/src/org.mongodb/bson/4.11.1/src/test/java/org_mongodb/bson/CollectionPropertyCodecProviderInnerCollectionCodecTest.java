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
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

public class CollectionPropertyCodecProviderInnerCollectionCodecTest {
    @Test
    void decodesConcreteCollectionPropertyUsingDeclaredConstructor() {
        final BsonArray values = new BsonArray();
        values.add(new BsonString("alpha"));
        values.add(BsonNull.VALUE);
        values.add(new BsonString("bravo"));
        final BsonDocument document = new BsonDocument("values", values);

        final Container decoded = decode(Container.class, document);

        assertThat(decoded.getValues()).isInstanceOf(ArrayList.class);
        assertThat(decoded.getValues()).containsExactly("alpha", null, "bravo");
    }

    private static <T> T decode(final Class<T> type, final BsonDocument document) {
        final PojoCodecProvider pojoCodecProvider = PojoCodecProvider.builder().register(type).build();
        final CodecRegistry registry = fromProviders(new ValueCodecProvider(), pojoCodecProvider);
        final Codec<T> codec = registry.get(type);

        return codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());
    }

    public static final class Container {
        private ArrayList<String> values;

        public Container() {
        }

        public ArrayList<String> getValues() {
            return values;
        }

        public void setValues(final ArrayList<String> values) {
            this.values = values;
        }
    }
}
